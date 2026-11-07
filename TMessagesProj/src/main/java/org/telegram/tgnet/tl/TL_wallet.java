package org.telegram.tgnet.tl;

import android.text.TextUtils;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLMethod;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.ui.Wallet.WalletEngine2.SendPhase;

import java.util.ArrayList;
import java.util.Objects;

public class TL_wallet {

    public static class WalletState extends TLObject {
        public static WalletState TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            WalletState result = null;
            switch (constructor) {
                case TL_walletStateEmpty.constructor:
                    result = new TL_walletStateEmpty();
                    break;
                case TL_walletState.constructor:
                    result = new TL_walletState();
                    break;
            }
            return TLdeserialize(WalletState.class, result, stream, constructor, exception);
        }
    }
    public static class TL_walletStateEmpty extends WalletState {
        public static final int constructor = 0x9cb9b2ec;

        public boolean creating;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            creating = hasFlag(flags, FLAG_0);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, creating);
            stream.writeInt32(flags);
        }
    }
    public static class TL_walletState extends WalletState {
        public static final int constructor = 0x95c5346b;

        public boolean backup_enabled;
        public boolean can_export_phrase;
        public boolean can_enable_backup;
        public String address;
        public byte[] public_key;
        public long balance;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            backup_enabled = hasFlag(flags, FLAG_0);
            can_export_phrase = hasFlag(flags, FLAG_1);
            can_enable_backup = hasFlag(flags, FLAG_2);
            address = stream.readString(exception);
            public_key = stream.readByteArray(exception);
            balance = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, backup_enabled);
            flags = setFlag(flags, FLAG_1, can_export_phrase);
            flags = setFlag(flags, FLAG_2, can_enable_backup);
            stream.writeInt32(flags);
            stream.writeString(address);
            stream.writeByteArray(public_key);
            stream.writeInt64(balance);
        }
    }

    public static class walletUserAddress extends TLObject {
        public static final int constructor = 0xfe78eecc;

        public long user_id;
        public String address;
        public byte[] public_key;

        public static walletUserAddress TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final walletUserAddress result = walletUserAddress.constructor != constructor ? null : new walletUserAddress();
            return TLdeserialize(walletUserAddress.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_0)) {
                user_id = stream.readInt64(exception);
            }
            address = stream.readString(exception);
            public_key = stream.readByteArray(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, user_id != 0);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt64(user_id);
            }
            stream.writeString(address);
            stream.writeByteArray(public_key);
        }
    }
    public static class userAddresses extends TLObject {
        public static final int constructor = 0x928e7b55;

        public ArrayList<walletUserAddress> addresses = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static userAddresses TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final userAddresses result = userAddresses.constructor != constructor ? null : new userAddresses();
            return TLdeserialize(userAddresses.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            addresses = Vector.deserialize(stream, walletUserAddress::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, addresses);
            Vector.serialize(stream, users);
        }
    }

    public static class WalletTransactionPeer extends TLObject {

        public long user_id;
        public long channel_id;
        public String address;
        public String domain;

        public static WalletTransactionPeer TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            WalletTransactionPeer result = null;
            switch (constructor) {
                case walletTransactionPeerUser.constructor:
                    result = new walletTransactionPeerUser();
                    break;
                case walletTransactionPeerAddress.constructor:
                    result = new walletTransactionPeerAddress();
                    break;
                case walletTransactionPeerUnsupported.constructor:
                    result = new walletTransactionPeerUnsupported();
                    break;
                case walletTransactionPeerOnramp.constructor:
                    result = new walletTransactionPeerOnramp();
                    break;
                case walletTransactionPeerChannel.constructor:
                    result = new walletTransactionPeerChannel();
                    break;
                case walletTransactionPeerTelegramGift.constructor:
                    result = new walletTransactionPeerTelegramGift();
                    break;
            }
            return TLdeserialize(WalletTransactionPeer.class, result, stream, constructor, exception);
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof WalletTransactionPeer)) return false;
            final WalletTransactionPeer peer = (WalletTransactionPeer) obj;
            if (peer instanceof walletTransactionPeerUser) {
                if (!(this instanceof walletTransactionPeerUser)) return false;
                final walletTransactionPeerUser a = (walletTransactionPeerUser) this;
                final walletTransactionPeerUser b = (walletTransactionPeerUser) peer;
                return a.user_id == b.user_id && TextUtils.equals(a.address, b.address);
            } else if (peer instanceof walletTransactionPeerChannel) {
                if (!(this instanceof walletTransactionPeerChannel)) return false;
                final walletTransactionPeerChannel a = (walletTransactionPeerChannel) this;
                final walletTransactionPeerChannel b = (walletTransactionPeerChannel) peer;
                return a.channel_id == b.channel_id && TextUtils.equals(a.address, b.address);
            } else if (peer instanceof walletTransactionPeerAddress) {
                if (!(this instanceof walletTransactionPeerAddress)) return false;
                final walletTransactionPeerAddress a = (walletTransactionPeerAddress) this;
                final walletTransactionPeerAddress b = (walletTransactionPeerAddress) peer;
                return TextUtils.equals(a.address, b.address);
            } else if (peer instanceof walletTransactionPeerTelegramGift) {
                if (!(this instanceof walletTransactionPeerTelegramGift)) return false;
                return ((walletTransactionPeerTelegramGift) this).own_profile == ((walletTransactionPeerTelegramGift) peer).own_profile;
            } else if (peer instanceof walletTransactionPeerUnsupported) {
                return this instanceof walletTransactionPeerUnsupported;
            }
            return false;
        }
    }
    public static class walletTransactionPeerUser extends WalletTransactionPeer {
        public static final int constructor = 0xd4ea706d;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            user_id = stream.readInt64(exception);
            address = stream.readString(exception);
            if (hasFlag(flags, FLAG_0)) {
                domain = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, domain != null);
            stream.writeInt32(flags);
            stream.writeInt64(user_id);
            stream.writeString(address);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(domain);
            }
        }
    }
    public static class walletTransactionPeerChannel extends WalletTransactionPeer {
        public static final int constructor = 0x54e70c31;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            channel_id = stream.readInt64(exception);
            address = stream.readString(exception);
            if (hasFlag(flags, FLAG_0)) {
                domain = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, domain != null);
            stream.writeInt32(flags);
            stream.writeInt64(channel_id);
            stream.writeString(address);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(domain);
            }
        }
    }
    public static class walletTransactionPeerAddress extends WalletTransactionPeer {
        public static final int constructor = 0x62cc1bc;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            address = stream.readString(exception);
            if (hasFlag(flags, FLAG_0)) {
                domain = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, domain != null);
            stream.writeInt32(flags);
            stream.writeString(address);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(domain);
            }
        }
    }
    public static class walletTransactionPeerOnramp extends WalletTransactionPeer {
        public static final int constructor = 0xe595186e;

        public String provider_name;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            address = stream.readString(exception);
            if (hasFlag(flags, FLAG_0)) {
                domain = stream.readString(exception);
            }
            provider_name = stream.readString(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, domain != null);
            stream.writeInt32(flags);
            stream.writeString(address);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(domain);
            }
            stream.writeString(provider_name);
        }
    }
    public static class walletTransactionPeerTelegramGift extends WalletTransactionPeer {
        public static final int constructor = 0xf4729499;

        public boolean own_profile;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            own_profile = hasFlag(flags, FLAG_0);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, own_profile);
            stream.writeInt32(flags);
        }
    }
    public static class walletTransactionPeerUnsupported extends WalletTransactionPeer {
        public static final int constructor = 0x728bed5a;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {}
        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    @Keep
    public static class walletTransaction extends TLObject {
        public static final int constructor = 0x952dc143;

        public static walletTransaction TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final walletTransaction result = walletTransaction.constructor != constructor ? null : new walletTransaction();
            return TLdeserialize(walletTransaction.class, result, stream, constructor, exception);
        }

        public boolean incoming;
        public boolean failed;
        public boolean key_change;
        public boolean comment_encrypted;
        public boolean gasless;
        public String id;
        public long amount;
        public long fee;
        public int date;
        public WalletTransactionPeer peer;
        public String comment;
        public String tx_hash;
        public nftItem nft;

        public boolean comment_encrypted_preparing; //custom, comment still contains local plaintext
        public boolean pending; //custom
        public SendPhase phase; //custom
        public boolean preview; //custom
        public boolean feeUnknown; //custom
        public String traceFeesNanograms; //custom
        public long validUntil; //custom
        public boolean traceSucceeded; //custom
        public boolean traceIncomplete; //custom
        public String operationId; //custom
        public long random_id; //custom, not serialized
        public int localMessageId; //custom, local chat message used by the send flight
        public String messageHash; //custom
        public String gaslessMessageBodyHash; //custom
        public String normalMessageBodyHash; //custom

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            incoming = hasFlag(flags, FLAG_0);
            gasless = hasFlag(flags, FLAG_1);
            failed = hasFlag(flags, FLAG_2);
            key_change = hasFlag(flags, FLAG_5);
            comment_encrypted = hasFlag(flags, FLAG_6);
            id = stream.readString(exception);
            amount = stream.readInt64(exception);
            fee = stream.readInt64(exception);
            date = stream.readInt32(exception);
            peer = WalletTransactionPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_3)) {
                comment = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                tx_hash = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                nft = nftItem.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, incoming);
            flags = setFlag(flags, FLAG_1, gasless);
            flags = setFlag(flags, FLAG_2, failed);
            flags = setFlag(flags, FLAG_3, comment != null);
            flags = setFlag(flags, FLAG_4, tx_hash != null);
            flags = setFlag(flags, FLAG_5, key_change);
            flags = setFlag(flags, FLAG_6, comment_encrypted);
            flags = setFlag(flags, FLAG_7, nft != null);
            stream.writeInt32(flags);
            stream.writeString(id);
            stream.writeInt64(amount);
            stream.writeInt64(fee);
            stream.writeInt32(date);
            peer.serializeToStream(stream);
            if (hasFlag(flags, FLAG_3)) {
                stream.writeString(comment);
            }
            if (hasFlag(flags, FLAG_4)) {
                stream.writeString(tx_hash);
            }
            if (hasFlag(flags, FLAG_7)) {
                nft.serializeToStream(stream);
            }
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof walletTransaction)) return false;
            final walletTransaction t = (walletTransaction) obj;
            return
                TextUtils.equals(this.id, t.id) &&
                this.incoming == t.incoming &&
                this.pending == t.pending &&
                this.key_change == t.key_change &&
                this.failed == t.failed &&
                this.amount == t.amount &&
                this.fee == t.fee &&
                this.date == t.date &&
                peerEquals(this.peer, t.peer) &&
                this.comment_encrypted == t.comment_encrypted &&
                this.comment_encrypted_preparing == t.comment_encrypted_preparing &&
                TextUtils.equals(this.comment, t.comment) &&
                TextUtils.equals(this.tx_hash, t.tx_hash) &&
                Objects.equals(this.nft, t.nft) &&
                this.preview == t.preview && //custom
                this.feeUnknown == t.feeUnknown && //custom
                Objects.equals(this.traceFeesNanograms, t.traceFeesNanograms) && //custom
                this.validUntil == t.validUntil && //custom
                this.traceSucceeded == t.traceSucceeded && //custom
                this.traceIncomplete == t.traceIncomplete && //custom
                Objects.equals(this.operationId, t.operationId) && //custom
                Objects.equals(this.messageHash, t.messageHash) && //custom
                this.phase == t.phase; //custom
        }

        private static boolean peerEquals(WalletTransactionPeer a, WalletTransactionPeer b) {
            return a == b || (a != null && b != null && a.equals(b));
        }

        public boolean set(walletTransaction t) {
            // assuming id is the same
            if (
                this.incoming != t.incoming ||
                this.pending != t.pending ||
                this.key_change != t.key_change ||
                this.failed != t.failed ||
                this.amount != t.amount ||
                this.fee != t.fee ||
                this.date != t.date ||
                !peerEquals(this.peer, t.peer) ||
                this.comment_encrypted != t.comment_encrypted ||
                this.comment_encrypted_preparing != t.comment_encrypted_preparing ||
                !TextUtils.equals(this.comment, t.comment) ||
                !TextUtils.equals(this.tx_hash, t.tx_hash) ||
                !Objects.equals(this.nft, t.nft) ||
                this.preview != t.preview || //custom
                this.feeUnknown != t.feeUnknown || //custom
                !TextUtils.equals(this.traceFeesNanograms, t.traceFeesNanograms) || //custom
                this.validUntil != t.validUntil || //custom
                this.traceSucceeded != t.traceSucceeded || //custom
                this.traceIncomplete != t.traceIncomplete || //custom
                !TextUtils.equals(this.operationId, t.operationId) || //custom
                !TextUtils.equals(this.messageHash, t.messageHash) || //custom
                this.phase != t.phase //custom
            ) {
                this.incoming = t.incoming;
                this.pending = t.pending;
                this.key_change = t.key_change;
                this.failed = t.failed;
                this.amount = t.amount;
                this.fee = t.fee;
                this.date = t.date;
                this.peer = t.peer;
                this.comment_encrypted = t.comment_encrypted;
                this.comment_encrypted_preparing = t.comment_encrypted_preparing;
                this.comment = t.comment;
                this.tx_hash = t.tx_hash;
                this.nft = t.nft;
                this.preview = t.preview; //custom
                this.feeUnknown = t.feeUnknown; //custom
                this.traceFeesNanograms = t.traceFeesNanograms; //custom
                this.validUntil = t.validUntil; //custom
                this.traceSucceeded = t.traceSucceeded; //custom
                this.traceIncomplete = t.traceIncomplete; //custom
                this.operationId = t.operationId; //custom
                this.messageHash = t.messageHash; //custom
                this.phase = t.phase; //custom
                return true;
            }
            return false;
        }
    }
    public static class walletTransactions extends TLObject {
        public static final int constructor = 0x4322d5a5;

        public long balance;
        public ArrayList<walletTransaction> transactions = new ArrayList<>();
        public String next_offset;
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static walletTransactions TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final walletTransactions result = walletTransactions.constructor != constructor ? null : new walletTransactions();
            return TLdeserialize(walletTransactions.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            balance = stream.readInt64(exception);
            transactions = Vector.deserialize(stream, walletTransaction::TLdeserialize, exception);
            if (hasFlag(flags, FLAG_0)) {
                next_offset = stream.readString(exception);
            }
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, next_offset != null);
            stream.writeInt32(flags);
            stream.writeInt64(balance);
            Vector.serialize(stream, transactions);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(next_offset);
            }
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);

        }
    }

    public static class secretPhraseParts extends TLObject {
        public static final int constructor = 0xe6d0ef01;

        public String token;
        public ArrayList<Integer> dcs = new ArrayList<>();

        public static secretPhraseParts TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final secretPhraseParts result = secretPhraseParts.constructor != constructor ? null : new secretPhraseParts();
            return TLdeserialize(secretPhraseParts.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            token = stream.readString(exception);
            dcs = Vector.deserializeInt(stream, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(token);
            Vector.serializeInt(stream, dcs);
        }
    }
    public static class encryptedSecretPhrasePart extends TLObject {
        public static final int constructor = 0x18e82537;

        public byte[] data;

        public static encryptedSecretPhrasePart TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final encryptedSecretPhrasePart result = encryptedSecretPhrasePart.constructor != constructor ? null : new encryptedSecretPhrasePart();
            return TLdeserialize(encryptedSecretPhrasePart.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            data = stream.readByteArray(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(data);
        }
    }
    public static class holderDc extends TLObject {
        public static final int constructor = 0xf9d612ef;

        public int dc;
        public byte[] public_key;

        public static holderDc TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final holderDc result = holderDc.constructor != constructor ? null : new holderDc();
            return TLdeserialize(holderDc.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            dc = stream.readInt32(exception);
            public_key = stream.readByteArray(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(dc);
            stream.writeByteArray(public_key);
        }
    }

    public static class InputWalletReplacement extends TLObject {
        public static InputWalletReplacement TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            InputWalletReplacement result = null;
            switch (constructor) {
                case inputWalletNew.constructor:
                    result = new inputWalletNew();
                    break;
                case inputWalletImported.constructor:
                    result = new inputWalletImported();
                    break;
            }
            return TLdeserialize(InputWalletReplacement.class, result, stream, constructor, exception);
        }
    }
    public static class inputWalletNew extends InputWalletReplacement {
        public static final int constructor = 0x63a440dc;
        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }
    public static class inputWalletImported extends InputWalletReplacement {
        public static final int constructor = 0x66a66a3b;

        public byte[] public_key;
        public byte[] anchor_public_key;
        public walletOwnershipProof proof;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            public_key = stream.readByteArray(exception);
            if (hasFlag(flags, FLAG_0)) {
                anchor_public_key = stream.readByteArray(exception);
            }
            proof = walletOwnershipProof.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, anchor_public_key != null);
            stream.writeInt32(flags);
            stream.writeByteArray(public_key);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeByteArray(anchor_public_key);
            }
            proof.serializeToStream(stream);
        }
    }

    public static class proofChallenge extends TLObject {
        public static final int constructor = 0x99e41707;

        public String payload;
        public int expires;
        public String domain;

        public static proofChallenge TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final proofChallenge result = proofChallenge.constructor != constructor ? null : new proofChallenge();
            return TLdeserialize(proofChallenge.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            payload = stream.readString(exception);
            expires = stream.readInt32(exception);
            domain = stream.readString(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(payload);
            stream.writeInt32(expires);
            stream.writeString(domain);
        }
    }
    public static class walletOwnershipProof extends TLObject {
        public static final int constructor = 0x60bccb0d;

        public int timestamp;
        public byte[] signature;

        public static walletOwnershipProof TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final walletOwnershipProof result = walletOwnershipProof.constructor != constructor ? null : new walletOwnershipProof();
            return TLdeserialize(walletOwnershipProof.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            timestamp = stream.readInt32(exception);
            signature = stream.readByteArray(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(timestamp);
            stream.writeByteArray(signature);
        }
    }
    public static class getProofChallenge extends TLMethod<proofChallenge> {
        public static final int constructor = 0x2025e697;

        @Override
        public proofChallenge deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return proofChallenge.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class getState extends TLMethod<WalletState> {
        public static final int constructor = 0xab83b33a;

        @Override
        public WalletState deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return WalletState.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }
    public static class getUserAddresses extends TLMethod<userAddresses> {
        public static final int constructor = 0x5275dfdd;

        public boolean force;
        public ArrayList<TLRPC.InputUser> id = new ArrayList<>();
        public ArrayList<String> addresses = new ArrayList<>();

        @Override
        public userAddresses deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return userAddresses.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, force);
            stream.writeInt32(flags);
            Vector.serialize(stream, id);
            Vector.serializeString(stream, addresses);
        }
    }
    public static class getTransactions extends TLMethod<walletTransactions> {
        public static final int constructor = 0xa8830a83;

        public boolean inbound;
        public boolean outbound;
        public String offset;
        public int limit;

        @Override
        public walletTransactions deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return walletTransactions.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, inbound);
            flags = setFlag(flags, FLAG_1, outbound);
            stream.writeInt32(flags);
            stream.writeString(offset);
            stream.writeInt32(limit);
        }
    }
    public static class getTransactionsByIDs extends TLMethod<walletTransactions> {
        public static final int constructor = 0x811ceab6;

        public ArrayList<String> id = new ArrayList<>();

        @Override
        public walletTransactions deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return walletTransactions.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serializeString(stream, id);
        }
    }
    public static class getTransactionsByMsgHash extends TLMethod<walletTransactions> {
        public static final int constructor = 0xa6bb795d;

        public ArrayList<String> msg_hash = new ArrayList<>();

        @Override
        public walletTransactions deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return walletTransactions.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serializeString(stream, msg_hash);
        }
    }
    public static class exportSecretPhrase extends TLMethod<secretPhraseParts> {
        public static final int constructor = 0x9077c9ac;

        public TLRPC.InputCheckPasswordSRP password;

        @Override
        public secretPhraseParts deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return secretPhraseParts.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, password != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                password.serializeToStream(stream);
            }
        }
    }
    public static class fetchEncryptedSecretPhrasePart extends TLMethod<encryptedSecretPhrasePart> {
        public static final int constructor = 0xf53925cf;

        public String token;
        public byte[] public_key;

        @Override
        public encryptedSecretPhrasePart deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return encryptedSecretPhrasePart.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(token);
            stream.writeByteArray(public_key);
        }
    }
    public static class replaceWallet extends TLMethod<WalletState> {
        public static final int constructor = 0xd8c72eec;

        public InputWalletReplacement wallet;
        public TLRPC.InputCheckPasswordSRP password;

        @Override
        public WalletState deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return WalletState.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, password != null);
            stream.writeInt32(flags);
            wallet.serializeToStream(stream);
            if (hasFlag(flags, FLAG_0)) {
                password.serializeToStream(stream);
            }
        }
    }
    public static class disableBackup extends TLMethod<WalletState> {
        public static final int constructor = 0xb0bf0da;

        public TLRPC.InputCheckPasswordSRP password;
        public byte[] new_public_key;
        public walletOwnershipProof proof;

        @Override
        public WalletState deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return WalletState.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, password != null);
            flags = setFlag(flags, FLAG_1, new_public_key != null);
            flags = setFlag(flags, FLAG_2, proof != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                password.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_1)) {
                stream.writeByteArray(new_public_key);
            }
            if (hasFlag(flags, FLAG_2)) {
                proof.serializeToStream(stream);
            }
        }
    }
    public static class getBackupHolderDcs extends TLMethod<Vector<holderDc>> {
        public static final int constructor = 0xd179d494;

        @Override
        public Vector<holderDc> deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserialize(stream, constructor, exception, holderDc::TLdeserialize);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }
    public static class enableBackup extends TLMethod<WalletState> {
        public static final int constructor = 0x4500c525;

        public ArrayList<byte[]> parts = new ArrayList<>();
        public byte[] new_public_key;
        public walletOwnershipProof proof;

        @Override
        public WalletState deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return WalletState.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_1, new_public_key != null);
            flags = setFlag(flags, FLAG_2, proof != null);
            stream.writeInt32(flags);
            Vector.serializeByteArray(stream, parts);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeByteArray(new_public_key);
            }
            if (hasFlag(flags, FLAG_2)) {
                proof.serializeToStream(stream);
            }
        }
    }

    public static class sendTransfer extends TLMethod<TLRPC.Updates> {
        public static final int constructor = 0xd9e5d9e2;

        public byte[] data_normal;
        public byte[] data_gasless;
        public TLRPC.InputUser user_id;
        public long random_id;
        public String gaslessMessageBodyHash; //custom, not serialized
        public String normalMessageBodyHash; //custom, not serialized

        @Override
        public TLRPC.Updates deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, data_gasless != null);
            stream.writeInt32(flags);
            stream.writeByteArray(data_normal);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeByteArray(data_gasless);
            }
            user_id.serializeToStream(stream);
            stream.writeInt64(random_id);
        }
    }
    public static class getGaslessInfo extends TLMethod<TLRPC.Updates> {
        public static final int constructor = 0x742df0a1;

        @Override
        public TLRPC.Updates deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class mnemonic_decryptedKeyPart extends TLObject {
        public static final int constructor = 0x8b90dd08;

        public static mnemonic_decryptedKeyPart TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final mnemonic_decryptedKeyPart result = mnemonic_decryptedKeyPart.constructor != constructor ? null : new mnemonic_decryptedKeyPart();
            return TLdeserialize(mnemonic_decryptedKeyPart.class, result, stream, constructor, exception);
        }

        public byte[] share;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(share);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            share = stream.readByteArray(exception);
        }
    }

    public static class currencyRate extends TLObject {
        public static final int constructor = 0x30d9743c;

        public static currencyRate TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final currencyRate result = currencyRate.constructor != constructor ? null : new currencyRate();
            return TLdeserialize(currencyRate.class, result, stream, constructor, exception);
        }

        public String currency;
        public double rate;
        public String symbol; // custom
        public String title; // custom
        public String translatedTitle; // custom
        public String thousandsSeparator; // custom
        public String decimalSeparator; // custom
        public boolean symbolLeft; // custom
        public boolean spaceBetween; // custom
        public boolean dropZeros; // custom
        public int exp; // custom

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            currency = stream.readString(exception);
            rate = stream.readDouble(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(currency);
            stream.writeDouble(rate);
        }
    }
    public static class currencyRates extends TLObject {
        public static final int constructor = 0xbbcce4c2;

        public static currencyRates TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final currencyRates result = currencyRates.constructor != constructor ? null : new currencyRates();
            return TLdeserialize(currencyRates.class, result, stream, constructor, exception);
        }

        public ArrayList<currencyRate> rates = new ArrayList<>();

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            rates = Vector.deserialize(stream, currencyRate::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, rates);
        }
    }
    public static class getCurrencyRates extends TLMethod<currencyRates> {
        public static final int constructor = 0xd35983e8;

        @Override
        public currencyRates deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return currencyRates.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class existingBalance extends TLObject {
        public static final int constructor = 0xbde90a8d;

        public boolean has_balance;
        public String url;

        public static existingBalance TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final existingBalance result = existingBalance.constructor != constructor ? null : new existingBalance();
            return TLdeserialize(existingBalance.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            has_balance = hasFlag(flags, FLAG_0);
            url = stream.readString(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, has_balance);
            stream.writeInt32(flags);
            stream.writeString(url);
        }
    }
    public static class getExistingWaltBalance extends TLMethod<existingBalance> {
        public static final int constructor = 0x691f4360;

        @Override
        public existingBalance deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return existingBalance.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class inputTonConnectOauthSession extends TLObject {
        public static final int constructor = 0x629f66ce;

        public long session_id;
        public byte[] challenge_answer;
        public byte[] body;
        public walletOwnershipProof proof;

        public static inputTonConnectOauthSession TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final inputTonConnectOauthSession result = inputTonConnectOauthSession.constructor != constructor ? null : new inputTonConnectOauthSession();
            return TLdeserialize(inputTonConnectOauthSession.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            session_id = stream.readInt64(exception);
            challenge_answer = stream.readByteArray(exception);
            body = stream.readByteArray(exception);
            proof = walletOwnershipProof.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(session_id);
            stream.writeByteArray(challenge_answer);
            stream.writeByteArray(body);
            proof.serializeToStream(stream);
        }
    }

    public static class tonConnectManifest extends TLObject {
        public static final int constructor = 0x12229264;

        public static tonConnectManifest TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final tonConnectManifest result = tonConnectManifest.constructor != constructor ? null : new tonConnectManifest();
            return TLdeserialize(tonConnectManifest.class, result, stream, constructor, exception);
        }

        public String url;
        public String name;
        public TLRPC.WebDocument icon;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, icon != null);
            stream.writeInt32(flags);
            stream.writeString(url);
            stream.writeString(name);
            if (hasFlag(flags, FLAG_0)) {
                icon.serializeToStream(stream);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            url = stream.readString(exception);
            name = stream.readString(exception);
            if (hasFlag(flags, FLAG_0)) {
                icon = TLRPC.WebDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }
    }
    public static class tonConnectSession extends TLObject {
        public static final int constructor = 0x126556c6;

        public boolean pending;
        public boolean closing;
        public boolean closed;
        public long id;
        public String dapp_client_id;
        public String client_id;
        public byte[] nonce;
        public tonConnectManifest manifest;
        public Integer manifest_error;
        public int date;

        public static tonConnectSession TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final tonConnectSession result = tonConnectSession.constructor != constructor ? null : new tonConnectSession();
            return TLdeserialize(tonConnectSession.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            pending = hasFlag(flags, FLAG_0);
            closing = hasFlag(flags, FLAG_1);
            closed = hasFlag(flags, FLAG_2);
            id = stream.readInt64(exception);
            dapp_client_id = stream.readString(exception);
            if (hasFlag(flags, FLAG_3)) {
                client_id = stream.readString(exception);
            }
            nonce = stream.readByteArray(exception);
            if (hasFlag(flags, FLAG_4)) {
                manifest = tonConnectManifest.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_5)) {
                manifest_error = stream.readInt32(exception);
            }
            date = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, pending);
            flags = setFlag(flags, FLAG_1, closing);
            flags = setFlag(flags, FLAG_2, closed);
            flags = setFlag(flags, FLAG_3, client_id != null);
            flags = setFlag(flags, FLAG_4, manifest != null);
            flags = setFlag(flags, FLAG_5, manifest_error != null);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeString(dapp_client_id);
            if (hasFlag(flags, FLAG_3)) {
                stream.writeString(client_id);
            }
            stream.writeByteArray(nonce);
            if (hasFlag(flags, FLAG_4)) {
                manifest.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_5)) {
                stream.writeInt32(manifest_error);
            }
            stream.writeInt32(date);
        }
    }
    public static class tonConnectChallenge extends TLObject {
        public static final int constructor = 0x4bc89693;

        public byte[] challenge;
        public long event_id;

        public static tonConnectChallenge TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final tonConnectChallenge result = tonConnectChallenge.constructor != constructor ? null : new tonConnectChallenge();
            return TLdeserialize(tonConnectChallenge.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            challenge = stream.readByteArray(exception);
            event_id = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(challenge);
            stream.writeInt64(event_id);
        }
    }
    public static class tonConnectNextEventId extends TLObject {
        public static final int constructor = 0x582464e3;

        public long event_id;

        public static tonConnectNextEventId TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final tonConnectNextEventId result = tonConnectNextEventId.constructor != constructor ? null : new tonConnectNextEventId();
            return TLdeserialize(tonConnectNextEventId.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            event_id = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(event_id);
        }
    }
    public static class tonConnectRequest extends TLObject {
        public static final int constructor = 0xa15f8513;

        public long session_id;
        public int msg_id;
        public byte[] body;
        public int expires;
        public String topic;
        public String trace_id;

        public static tonConnectRequest TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final tonConnectRequest result = tonConnectRequest.constructor != constructor ? null : new tonConnectRequest();
            return TLdeserialize(tonConnectRequest.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            session_id = stream.readInt64(exception);
            msg_id = stream.readInt32(exception);
            body = stream.readByteArray(exception);
            expires = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_0)) {
                topic = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_1)) {
                trace_id = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, topic != null);
            flags = setFlag(flags, FLAG_1, trace_id != null);
            stream.writeInt32(flags);
            stream.writeInt64(session_id);
            stream.writeInt32(msg_id);
            stream.writeByteArray(body);
            stream.writeInt32(expires);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(topic);
            }
            if (hasFlag(flags, FLAG_1)) {
                stream.writeString(trace_id);
            }
        }
    }
    public static class tonConnectPending extends TLObject {
        public static final int constructor = 0x85c0f124;

        public tonConnectSession session;
        public ArrayList<tonConnectRequest> requests = new ArrayList<>();

        public static tonConnectPending TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final tonConnectPending result = tonConnectPending.constructor != constructor ? null : new tonConnectPending();
            return TLdeserialize(tonConnectPending.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            session = tonConnectSession.TLdeserialize(stream, stream.readInt32(exception), exception);
            requests = Vector.deserialize(stream, tonConnectRequest::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            session.serializeToStream(stream);
            Vector.serialize(stream, requests);
        }
    }
    public static class tonConnectCreateSession extends TLMethod<tonConnectSession> {
        public static final int constructor = 0xcc931046;

        public String dapp_client_id;
        public String manifest_url;

        @Override
        public tonConnectSession deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return tonConnectSession.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(dapp_client_id);
            stream.writeString(manifest_url);
        }
    }
    public static class tonConnectRegisterKey extends TLMethod<tonConnectChallenge> {
        public static final int constructor = 0x338bcf1c;

        public long session_id;
        public String client_id;

        @Override
        public tonConnectChallenge deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return tonConnectChallenge.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(session_id);
            stream.writeString(client_id);
        }
    }
    public static class tonConnectSubmitConnectResult extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0xc2c00779;

        public boolean error;
        public long session_id;
        public byte[] challenge_answer;
        public byte[] body;
        public String trace_id;

        @Override
        public TLRPC.Bool deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, error);
            flags = setFlag(flags, FLAG_1, trace_id != null);
            stream.writeInt32(flags);
            stream.writeInt64(session_id);
            stream.writeByteArray(challenge_answer);
            stream.writeByteArray(body);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeString(trace_id);
            }
        }
    }
    public static class tonConnectGetPending extends TLMethod<tonConnectPending> {
        public static final int constructor = 0x11aee065;

        public String dapp_client_id;
        public Long session_id;

        @Override
        public tonConnectPending deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return tonConnectPending.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, dapp_client_id != null);
            flags = setFlag(flags, FLAG_1, session_id != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(dapp_client_id);
            }
            if (hasFlag(flags, FLAG_1)) {
                stream.writeInt64(session_id);
            }
        }
    }
    public static class tonConnectClaimRequest extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0x66b57b41;

        public boolean declined;
        public long session_id;
        public int msg_id;
        public String app_request_id;
        public byte[] challenge_answer;

        @Override
        public TLRPC.Bool deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, challenge_answer != null);
            flags = setFlag(flags, FLAG_1, declined);
            stream.writeInt32(flags);
            stream.writeInt64(session_id);
            stream.writeInt32(msg_id);
            stream.writeString(app_request_id);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeByteArray(challenge_answer);
            }
        }
    }
    public static class tonConnectSubmitResponse extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0xfac67d3c;

        public long session_id;
        public int msg_id;
        public byte[] body;
        public String trace_id;

        @Override
        public TLRPC.Bool deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, trace_id != null);
            stream.writeInt32(flags);
            stream.writeInt64(session_id);
            stream.writeInt32(msg_id);
            stream.writeByteArray(body);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(trace_id);
            }
        }
    }
    public static class tonConnectGetNextEventId extends TLMethod<tonConnectNextEventId> {
        public static final int constructor = 0x775d7244;

        public long session_id;

        @Override
        public tonConnectNextEventId deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return tonConnectNextEventId.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(session_id);
        }
    }
    public static class tonConnectCloseSession extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0xd63f61ce;

        public long session_id;
        public byte[] body;

        @Override
        public TLRPC.Bool deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, body != null);
            stream.writeInt32(flags);
            stream.writeInt64(session_id);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeByteArray(body);
            }
        }
    }
    public static class tonConnectGetSessions extends TLMethod<tonConnectSessions> {
        public static final int constructor = 0xa7ffb56e;

        @Override
        public tonConnectSessions deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return tonConnectSessions.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }
    public static class tonConnectSessions extends TLObject {
        public static final int constructor = 0x0e1f6896;

        public ArrayList<tonConnectSession> sessions = new ArrayList<>();

        public static tonConnectSessions TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final tonConnectSessions result = tonConnectSessions.constructor != constructor ? null : new tonConnectSessions();
            return TLdeserialize(tonConnectSessions.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            sessions = Vector.deserialize(stream, tonConnectSession::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, sessions);
        }
    }

    public static class nftAttribute extends TLObject {
        public static final int constructor = 0x4c1ef10e;

        public String trait_type;
        public String value;

        public static nftAttribute TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final nftAttribute result = nftAttribute.constructor != constructor ? null : new nftAttribute();
            return TLdeserialize(nftAttribute.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            trait_type = stream.readString(exception);
            value = stream.readString(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(trait_type);
            stream.writeString(value);
        }
    }
    public static class nftItem extends TLObject {
        public static final int constructor = 0x173dfaf3;

        public String collection_address;
        public String address;
        public String owner_address;
        public String index;
        public String name;
        public String description;
        public TLRPC.WebDocument image;
        public TLRPC.WebDocument image_small;
        public TLRPC.WebDocument content_url;
        public TLRPC.WebDocument lottie;
        public ArrayList<nftAttribute> attributes = new ArrayList<>();
        public TLRPC.TL_dataJSON extra;
        public TL_stars.StarGift gift;

        public static nftItem TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final nftItem result = nftItem.constructor != constructor ? null : new nftItem();
            return TLdeserialize(nftItem.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_0)) {
                collection_address = stream.readString(exception);
            }
            address = stream.readString(exception);
            owner_address = stream.readString(exception);
            index = stream.readString(exception);
            if (hasFlag(flags, FLAG_1)) {
                name = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                description = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                image = TLRPC.WebDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                image_small = TLRPC.WebDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_5)) {
                content_url = TLRPC.WebDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_6)) {
                lottie = TLRPC.WebDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                attributes = Vector.deserialize(stream, nftAttribute::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_8)) {
                extra = TLRPC.TL_dataJSON.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_9)) {
                gift = TL_stars.StarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, collection_address != null);
            flags = setFlag(flags, FLAG_1, name != null);
            flags = setFlag(flags, FLAG_2, description != null);
            flags = setFlag(flags, FLAG_3, image != null);
            flags = setFlag(flags, FLAG_4, image_small != null);
            flags = setFlag(flags, FLAG_5, content_url != null);
            flags = setFlag(flags, FLAG_6, lottie != null);
            flags = setFlag(flags, FLAG_7, attributes != null && !attributes.isEmpty());
            flags = setFlag(flags, FLAG_8, extra != null);
            flags = setFlag(flags, FLAG_9, gift != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(collection_address);
            }
            stream.writeString(address);
            stream.writeString(owner_address);
            stream.writeString(index);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeString(name);
            }
            if (hasFlag(flags, FLAG_2)) {
                stream.writeString(description);
            }
            if (hasFlag(flags, FLAG_3)) {
                image.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_4)) {
                image_small.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_5)) {
                content_url.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                lottie.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, attributes);
            }
            if (hasFlag(flags, FLAG_8)) {
                extra.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_9)) {
                gift.serializeToStream(stream);
            }
        }

        public boolean isGift() {
            return (
                content_url != null && content_url.url != null && content_url.url.startsWith("https://nft.fragment.com/gift/") ||
                image != null && image.url != null && image.url.startsWith("https://nft.fragment.com/gift/")
            );
        }
        public boolean isUsername() {
            return (
                content_url != null && content_url.url != null && content_url.url.startsWith("https://nft.fragment.com/username/") ||
                image != null && image.url != null && image.url.startsWith("https://nft.fragment.com/username/")
            );
        }
        public boolean isPhoneNumber() {
            return (
                content_url != null && content_url.url != null && content_url.url.startsWith("https://nft.fragment.com/number/") ||
                image != null && image.url != null && image.url.startsWith("https://nft.fragment.com/number/")
            );
        }
    }
    public static class nftItems extends TLObject {
        public static final int constructor = 0x794d486f;

        public ArrayList<nftItem> items = new ArrayList<>();
        public String next_offset;

        public static nftItems TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final nftItems result = nftItems.constructor != constructor ? null : new nftItems();
            return TLdeserialize(nftItems.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            items = Vector.deserialize(stream, nftItem::TLdeserialize, exception);
            if (hasFlag(flags, FLAG_0)) {
                next_offset = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, next_offset != null);
            stream.writeInt32(flags);
            Vector.serialize(stream, items);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(next_offset);
            }
        }
    }
    public static class getNfts extends TLMethod<nftItems> {
        public static final int constructor = 0x8c12f3a4;

        public String offset;
        public int limit;

        @Override
        public nftItems deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return nftItems.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(offset);
            stream.writeInt32(limit);
        }
    }

    public static class inputWalletCardSticker extends TLObject {
        public static final int constructor = 0x57df45a7;

        public TL_stories.MediaAreaCoordinates coordinates;
        public String nft_address;

        public static inputWalletCardSticker TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final inputWalletCardSticker result = inputWalletCardSticker.constructor != constructor ? null : new inputWalletCardSticker();
            return TLdeserialize(inputWalletCardSticker.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            coordinates = TL_stories.MediaAreaCoordinates.TLdeserialize(stream, stream.readInt32(exception), exception);
            nft_address = stream.readString(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            coordinates.serializeToStream(stream);
            stream.writeString(nft_address);
        }
    }
    public static class cardSticker extends TLObject {
        public static final int constructor = 0xf3010cba;

        public TL_stories.MediaAreaCoordinates coordinates;
        public nftItem nft;

        public static cardSticker TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final cardSticker result = cardSticker.constructor != constructor ? null : new cardSticker();
            return TLdeserialize(cardSticker.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            coordinates = TL_stories.MediaAreaCoordinates.TLdeserialize(stream, stream.readInt32(exception), exception);
            nft = nftItem.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            coordinates.serializeToStream(stream);
            nft.serializeToStream(stream);
        }
    }
    public static class card extends TLObject {
        public static final int constructor = 0x8ed043b1;

        public ArrayList<cardSticker> stickers = new ArrayList<>();
        public nftItem bg_nft_item;
        public String address;
        public long balance;

        public static card TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final card result = card.constructor != constructor ? null : new card();
            return TLdeserialize(card.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_0)) {
                stickers = Vector.deserialize(stream, cardSticker::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_1)) {
                bg_nft_item = nftItem.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            address = stream.readString(exception);
            balance = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, stickers != null && !stickers.isEmpty());
            flags = setFlag(flags, FLAG_1, bg_nft_item != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                Vector.serialize(stream, stickers);
            }
            if (hasFlag(flags, FLAG_1)) {
                bg_nft_item.serializeToStream(stream);
            }
            stream.writeString(address);
            stream.writeInt64(balance);
        }
    }
    public static class setCardStickers extends TLMethod<card> {
        public static final int constructor = 0x068509ac;

        public ArrayList<inputWalletCardSticker> stickers = new ArrayList<>();
        public String bg_nft_address;

        @Override
        public card deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return card.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, bg_nft_address != null);
            stream.writeInt32(flags);
            Vector.serialize(stream, stickers);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(bg_nft_address);
            }
        }
    }

}
