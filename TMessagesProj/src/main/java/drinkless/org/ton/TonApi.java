package drinkless.org.ton;

public class TonApi {
    /**
     * This class is a base class for all tonlib interface classes.
     */
    public abstract static class Object {
        /**
         * @return string representation of the object.
         */
        public native String toString();

        /**
         * @return identifier uniquely determining type of the object.
         */
        public abstract int getConstructor();
    }

    /**
     * This class is a base class for all tonlib interface function-classes.
     */
    public abstract static class Function extends Object {
        /**
         * @return string representation of the object.
         */
        public native String toString();
    }

    /**
     *
     */
    public static class AccountAddress extends Object {
        public String accountAddress;

        /**
         *
         */
        public AccountAddress() {
        }

        public AccountAddress(String accountAddress) {
            this.accountAddress = accountAddress;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 755613099;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class Bip39Hints extends Object {
        public String[] words;

        /**
         *
         */
        public Bip39Hints() {
        }

        public Bip39Hints(String[] words) {
            this.words = words;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1012243456;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class Config extends Object {
        public String config;
        public String blockchainName;
        public boolean useCallbacksForNetwork;
        public boolean ignoreCache;

        /**
         *
         */
        public Config() {
        }

        public Config(String config, String blockchainName, boolean useCallbacksForNetwork, boolean ignoreCache) {
            this.config = config;
            this.blockchainName = blockchainName;
            this.useCallbacksForNetwork = useCallbacksForNetwork;
            this.ignoreCache = ignoreCache;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1538391496;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class Data extends Object {
        public byte[] bytes;

        /**
         *
         */
        public Data() {
        }

        public Data(byte[] bytes) {
            this.bytes = bytes;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -414733967;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class Error extends Object {
        public int code;
        public String message;

        /**
         *
         */
        public Error() {
        }

        public Error(int code, String message) {
            this.code = code;
            this.message = message;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1679978726;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class ExportedEncryptedKey extends Object {
        public byte[] data;

        /**
         *
         */
        public ExportedEncryptedKey() {
        }

        public ExportedEncryptedKey(byte[] data) {
            this.data = data;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 2024406612;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class ExportedKey extends Object {
        public String[] wordList;

        /**
         *
         */
        public ExportedKey() {
        }

        public ExportedKey(String[] wordList) {
            this.wordList = wordList;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1449248297;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class ExportedPemKey extends Object {
        public String pem;

        /**
         *
         */
        public ExportedPemKey() {
        }

        public ExportedPemKey(String pem) {
            this.pem = pem;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1425473725;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class Fees extends Object {
        public long inFwdFee;
        public long storageFee;
        public long gasFee;
        public long fwdFee;

        /**
         *
         */
        public Fees() {
        }

        public Fees(long inFwdFee, long storageFee, long gasFee, long fwdFee) {
            this.inFwdFee = inFwdFee;
            this.storageFee = storageFee;
            this.gasFee = gasFee;
            this.fwdFee = fwdFee;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1676273340;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public abstract static class InputKey extends Object {
    }

    public static class InputKeyRegular extends InputKey {
        public Key key;
        public byte[] localPassword;

        /**
         *
         */
        public InputKeyRegular() {
        }

        public InputKeyRegular(Key key, byte[] localPassword) {
            this.key = key;
            this.localPassword = localPassword;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -555399522;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public static class InputKeyFake extends InputKey {

        /**
         *
         */
        public InputKeyFake() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1074054722;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class Key extends Object {
        public String publicKey;
        public byte[] secret;

        /**
         *
         */
        public Key() {
        }

        public Key(String publicKey, byte[] secret) {
            this.publicKey = publicKey;
            this.secret = secret;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1978362923;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public abstract static class KeyStoreType extends Object {
    }

    public static class KeyStoreTypeDirectory extends KeyStoreType {
        public String directory;

        /**
         *
         */
        public KeyStoreTypeDirectory() {
        }

        public KeyStoreTypeDirectory(String directory) {
            this.directory = directory;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -378990038;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public static class KeyStoreTypeInMemory extends KeyStoreType {

        /**
         *
         */
        public KeyStoreTypeInMemory() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -2106848825;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     * This class is an abstract base class.
     * Describes a stream to which tonlib internal log is written.
     */
    public abstract static class LogStream extends Object {
    }

    /**
     * The log is written to stderr or an OS specific log.
     */
    public static class LogStreamDefault extends LogStream {

        /**
         * The log is written to stderr or an OS specific log.
         */
        public LogStreamDefault() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1390581436;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     * The log is written to a file.
     */
    public static class LogStreamFile extends LogStream {
        /**
         * Path to the file to where the internal tonlib log will be written.
         */
        public String path;
        /**
         * Maximum size of the file to where the internal tonlib log is written before the file will be auto-rotated.
         */
        public long maxFileSize;

        /**
         * The log is written to a file.
         */
        public LogStreamFile() {
        }

        /**
         * The log is written to a file.
         *
         * @param path Path to the file to where the internal tonlib log will be written.
         * @param maxFileSize Maximum size of the file to where the internal tonlib log is written before the file will be auto-rotated.
         */
        public LogStreamFile(String path, long maxFileSize) {
            this.path = path;
            this.maxFileSize = maxFileSize;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1880085930;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     * The log is written nowhere.
     */
    public static class LogStreamEmpty extends LogStream {

        /**
         * The log is written nowhere.
         */
        public LogStreamEmpty() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -499912244;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     * Contains a list of available tonlib internal log tags.
     */
    public static class LogTags extends Object {
        /**
         * List of log tags.
         */
        public String[] tags;

        /**
         * Contains a list of available tonlib internal log tags.
         */
        public LogTags() {
        }

        /**
         * Contains a list of available tonlib internal log tags.
         *
         * @param tags List of log tags.
         */
        public LogTags(String[] tags) {
            this.tags = tags;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1604930601;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     * Contains a tonlib internal log verbosity level.
     */
    public static class LogVerbosityLevel extends Object {
        /**
         * Log verbosity level.
         */
        public int verbosityLevel;

        /**
         * Contains a tonlib internal log verbosity level.
         */
        public LogVerbosityLevel() {
        }

        /**
         * Contains a tonlib internal log verbosity level.
         *
         * @param verbosityLevel Log verbosity level.
         */
        public LogVerbosityLevel(int verbosityLevel) {
            this.verbosityLevel = verbosityLevel;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1734624234;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class Ok extends Object {

        /**
         *
         */
        public Ok() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -722616727;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class Options extends Object {
        public Config config;
        public KeyStoreType keystoreType;

        /**
         *
         */
        public Options() {
        }

        public Options(Config config, KeyStoreType keystoreType) {
            this.config = config;
            this.keystoreType = keystoreType;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1924388359;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class SendGramsResult extends Object {
        public long sentUntil;
        public byte[] bodyHash;

        /**
         *
         */
        public SendGramsResult() {
        }

        public SendGramsResult(long sentUntil, byte[] bodyHash) {
            this.sentUntil = sentUntil;
            this.bodyHash = bodyHash;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 426872238;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public abstract static class SyncState extends Object {
    }

    public static class SyncStateDone extends SyncState {

        /**
         *
         */
        public SyncStateDone() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1408448777;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public static class SyncStateInProgress extends SyncState {
        public int fromSeqno;
        public int toSeqno;
        public int currentSeqno;

        /**
         *
         */
        public SyncStateInProgress() {
        }

        public SyncStateInProgress(int fromSeqno, int toSeqno, int currentSeqno) {
            this.fromSeqno = fromSeqno;
            this.toSeqno = toSeqno;
            this.currentSeqno = currentSeqno;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 107726023;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class UnpackedAccountAddress extends Object {
        public int workchainId;
        public boolean bounceable;
        public boolean testnet;
        public byte[] addr;

        /**
         *
         */
        public UnpackedAccountAddress() {
        }

        public UnpackedAccountAddress(int workchainId, boolean bounceable, boolean testnet, byte[] addr) {
            this.workchainId = workchainId;
            this.bounceable = bounceable;
            this.testnet = testnet;
            this.addr = addr;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1892946998;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public abstract static class Update extends Object {
    }

    public static class UpdateSendLiteServerQuery extends Update {
        public long id;
        public byte[] data;

        /**
         *
         */
        public UpdateSendLiteServerQuery() {
        }

        public UpdateSendLiteServerQuery(long id, byte[] data) {
            this.id = id;
            this.data = data;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1555130916;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public static class UpdateSyncState extends Update {
        public SyncState syncState;

        /**
         *
         */
        public UpdateSyncState() {
        }

        public UpdateSyncState(SyncState syncState) {
            this.syncState = syncState;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1204298718;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public abstract static class GenericAccountState extends Object {
    }

    public static class GenericAccountStateRaw extends GenericAccountState {
        public RawAccountState accountState;

        /**
         *
         */
        public GenericAccountStateRaw() {
        }

        public GenericAccountStateRaw(RawAccountState accountState) {
            this.accountState = accountState;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1387096685;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public static class GenericAccountStateTestWallet extends GenericAccountState {
        public TestWalletAccountState accountState;

        /**
         *
         */
        public GenericAccountStateTestWallet() {
        }

        public GenericAccountStateTestWallet(TestWalletAccountState accountState) {
            this.accountState = accountState;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1041955397;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public static class GenericAccountStateWallet extends GenericAccountState {
        public WalletAccountState accountState;

        /**
         *
         */
        public GenericAccountStateWallet() {
        }

        public GenericAccountStateWallet(WalletAccountState accountState) {
            this.accountState = accountState;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 942582925;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public static class GenericAccountStateWalletV3 extends GenericAccountState {
        public WalletV3AccountState accountState;

        /**
         *
         */
        public GenericAccountStateWalletV3() {
        }

        public GenericAccountStateWalletV3(WalletV3AccountState accountState) {
            this.accountState = accountState;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -281349583;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public static class GenericAccountStateTestGiver extends GenericAccountState {
        public TestGiverAccountState accountState;

        /**
         *
         */
        public GenericAccountStateTestGiver() {
        }

        public GenericAccountStateTestGiver(TestGiverAccountState accountState) {
            this.accountState = accountState;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1134654598;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public static class GenericAccountStateUninited extends GenericAccountState {
        public UninitedAccountState accountState;

        /**
         *
         */
        public GenericAccountStateUninited() {
        }

        public GenericAccountStateUninited(UninitedAccountState accountState) {
            this.accountState = accountState;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -908702008;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class InternalTransactionId extends Object {
        public long lt;
        public byte[] hash;

        /**
         *
         */
        public InternalTransactionId() {
        }

        public InternalTransactionId(long lt, byte[] hash) {
            this.lt = lt;
            this.hash = hash;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -989527262;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class LiteServerInfo extends Object {
        public long now;
        public int version;
        public long capabilities;

        /**
         *
         */
        public LiteServerInfo() {
        }

        public LiteServerInfo(long now, int version, long capabilities) {
            this.now = now;
            this.version = version;
            this.capabilities = capabilities;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1250165133;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class OptionsConfigInfo extends Object {
        public long defaultWalletId;

        /**
         *
         */
        public OptionsConfigInfo() {
        }

        public OptionsConfigInfo(long defaultWalletId) {
            this.defaultWalletId = defaultWalletId;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 165216422;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class QueryFees extends Object {
        public Fees sourceFees;
        public Fees destinationFees;

        /**
         *
         */
        public QueryFees() {
        }

        public QueryFees(Fees sourceFees, Fees destinationFees) {
            this.sourceFees = sourceFees;
            this.destinationFees = destinationFees;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 725267759;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class QueryInfo extends Object {
        public long id;
        public long validUntil;
        public byte[] bodyHash;

        /**
         *
         */
        public QueryInfo() {
        }

        public QueryInfo(long id, long validUntil, byte[] bodyHash) {
            this.id = id;
            this.validUntil = validUntil;
            this.bodyHash = bodyHash;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1588635915;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class RawAccountState extends Object {
        public long balance;
        public byte[] code;
        public byte[] data;
        public InternalTransactionId lastTransactionId;
        public byte[] frozenHash;
        public long syncUtime;

        /**
         *
         */
        public RawAccountState() {
        }

        public RawAccountState(long balance, byte[] code, byte[] data, InternalTransactionId lastTransactionId, byte[] frozenHash, long syncUtime) {
            this.balance = balance;
            this.code = code;
            this.data = data;
            this.lastTransactionId = lastTransactionId;
            this.frozenHash = frozenHash;
            this.syncUtime = syncUtime;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1205935434;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class RawInitialAccountState extends Object {
        public byte[] code;
        public byte[] data;

        /**
         *
         */
        public RawInitialAccountState() {
        }

        public RawInitialAccountState(byte[] code, byte[] data) {
            this.code = code;
            this.data = data;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 777456197;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class RawMessage extends Object {
        public String source;
        public String destination;
        public long value;
        public long fwdFee;
        public long ihrFee;
        public long createdLt;
        public byte[] bodyHash;
        public byte[] message;

        /**
         *
         */
        public RawMessage() {
        }

        public RawMessage(String source, String destination, long value, long fwdFee, long ihrFee, long createdLt, byte[] bodyHash, byte[] message) {
            this.source = source;
            this.destination = destination;
            this.value = value;
            this.fwdFee = fwdFee;
            this.ihrFee = ihrFee;
            this.createdLt = createdLt;
            this.bodyHash = bodyHash;
            this.message = message;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -906281442;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class RawTransaction extends Object {
        public long utime;
        public byte[] data;
        public InternalTransactionId transactionId;
        public long fee;
        public long storageFee;
        public long otherFee;
        public RawMessage inMsg;
        public RawMessage[] outMsgs;

        /**
         *
         */
        public RawTransaction() {
        }

        public RawTransaction(long utime, byte[] data, InternalTransactionId transactionId, long fee, long storageFee, long otherFee, RawMessage inMsg, RawMessage[] outMsgs) {
            this.utime = utime;
            this.data = data;
            this.transactionId = transactionId;
            this.fee = fee;
            this.storageFee = storageFee;
            this.otherFee = otherFee;
            this.inMsg = inMsg;
            this.outMsgs = outMsgs;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1887601793;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class RawTransactions extends Object {
        public RawTransaction[] transactions;
        public InternalTransactionId previousTransactionId;

        /**
         *
         */
        public RawTransactions() {
        }

        public RawTransactions(RawTransaction[] transactions, InternalTransactionId previousTransactionId) {
            this.transactions = transactions;
            this.previousTransactionId = previousTransactionId;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -2063931155;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class SmcInfo extends Object {
        public long id;

        /**
         *
         */
        public SmcInfo() {
        }

        public SmcInfo(long id) {
            this.id = id;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1134270012;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public abstract static class SmcMethodId extends Object {
    }

    public static class SmcMethodIdNumber extends SmcMethodId {
        public int number;

        /**
         *
         */
        public SmcMethodIdNumber() {
        }

        public SmcMethodIdNumber(int number) {
            this.number = number;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1541162500;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public static class SmcMethodIdName extends SmcMethodId {
        public String name;

        /**
         *
         */
        public SmcMethodIdName() {
        }

        public SmcMethodIdName(String name) {
            this.name = name;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -249036908;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class SmcRunResult extends Object {
        public long gasUsed;
        public TvmStackEntry[] stack;
        public int exitCode;

        /**
         *
         */
        public SmcRunResult() {
        }

        public SmcRunResult(long gasUsed, TvmStackEntry[] stack, int exitCode) {
            this.gasUsed = gasUsed;
            this.stack = stack;
            this.exitCode = exitCode;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1413805043;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class TestGiverAccountState extends Object {
        public long balance;
        public int seqno;
        public InternalTransactionId lastTransactionId;
        public long syncUtime;

        /**
         *
         */
        public TestGiverAccountState() {
        }

        public TestGiverAccountState(long balance, int seqno, InternalTransactionId lastTransactionId, long syncUtime) {
            this.balance = balance;
            this.seqno = seqno;
            this.lastTransactionId = lastTransactionId;
            this.syncUtime = syncUtime;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 860930426;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class TestWalletAccountState extends Object {
        public long balance;
        public int seqno;
        public InternalTransactionId lastTransactionId;
        public long syncUtime;

        /**
         *
         */
        public TestWalletAccountState() {
        }

        public TestWalletAccountState(long balance, int seqno, InternalTransactionId lastTransactionId, long syncUtime) {
            this.balance = balance;
            this.seqno = seqno;
            this.lastTransactionId = lastTransactionId;
            this.syncUtime = syncUtime;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 305698744;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class TestWalletInitialAccountState extends Object {
        public String publicKey;

        /**
         *
         */
        public TestWalletInitialAccountState() {
        }

        public TestWalletInitialAccountState(String publicKey) {
            this.publicKey = publicKey;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1231516227;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class TvmCell extends Object {
        public String bytes;

        /**
         *
         */
        public TvmCell() {
        }

        public TvmCell(String bytes) {
            this.bytes = bytes;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -859530316;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class TvmNumberDecimal extends Object {
        public String number;

        /**
         *
         */
        public TvmNumberDecimal() {
        }

        public TvmNumberDecimal(String number) {
            this.number = number;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1172477619;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class TvmSlice extends Object {
        public String bytes;

        /**
         *
         */
        public TvmSlice() {
        }

        public TvmSlice(String bytes) {
            this.bytes = bytes;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1069968387;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public abstract static class TvmStackEntry extends Object {
    }

    public static class TvmStackEntrySlice extends TvmStackEntry {
        public TvmSlice slice;

        /**
         *
         */
        public TvmStackEntrySlice() {
        }

        public TvmStackEntrySlice(TvmSlice slice) {
            this.slice = slice;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1395485477;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public static class TvmStackEntryCell extends TvmStackEntry {
        public TvmCell cell;

        /**
         *
         */
        public TvmStackEntryCell() {
        }

        public TvmStackEntryCell(TvmCell cell) {
            this.cell = cell;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1303473952;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public static class TvmStackEntryNumber extends TvmStackEntry {
        public TvmNumberDecimal number;

        /**
         *
         */
        public TvmStackEntryNumber() {
        }

        public TvmStackEntryNumber(TvmNumberDecimal number) {
            this.number = number;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1358642622;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    public static class TvmStackEntryUnsupported extends TvmStackEntry {

        /**
         *
         */
        public TvmStackEntryUnsupported() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 378880498;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class UninitedAccountState extends Object {
        public long balance;
        public InternalTransactionId lastTransactionId;
        public byte[] frozenHash;
        public long syncUtime;

        /**
         *
         */
        public UninitedAccountState() {
        }

        public UninitedAccountState(long balance, InternalTransactionId lastTransactionId, byte[] frozenHash, long syncUtime) {
            this.balance = balance;
            this.lastTransactionId = lastTransactionId;
            this.frozenHash = frozenHash;
            this.syncUtime = syncUtime;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -918880075;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class WalletAccountState extends Object {
        public long balance;
        public int seqno;
        public InternalTransactionId lastTransactionId;
        public long syncUtime;

        /**
         *
         */
        public WalletAccountState() {
        }

        public WalletAccountState(long balance, int seqno, InternalTransactionId lastTransactionId, long syncUtime) {
            this.balance = balance;
            this.seqno = seqno;
            this.lastTransactionId = lastTransactionId;
            this.syncUtime = syncUtime;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1919815977;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class WalletInitialAccountState extends Object {
        public String publicKey;

        /**
         *
         */
        public WalletInitialAccountState() {
        }

        public WalletInitialAccountState(String publicKey) {
            this.publicKey = publicKey;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1079249978;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class WalletV3AccountState extends Object {
        public long balance;
        public long walletId;
        public int seqno;
        public InternalTransactionId lastTransactionId;
        public long syncUtime;

        /**
         *
         */
        public WalletV3AccountState() {
        }

        public WalletV3AccountState(long balance, long walletId, int seqno, InternalTransactionId lastTransactionId, long syncUtime) {
            this.balance = balance;
            this.walletId = walletId;
            this.seqno = seqno;
            this.lastTransactionId = lastTransactionId;
            this.syncUtime = syncUtime;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1977698154;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     */
    public static class WalletV3InitialAccountState extends Object {
        public String publicKey;
        public long walletId;

        /**
         *
         */
        public WalletV3InitialAccountState() {
        }

        public WalletV3InitialAccountState(String publicKey, long walletId) {
            this.publicKey = publicKey;
            this.walletId = walletId;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 283460879;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     * Adds a message to tonlib internal log. This is an offline method. Can be called before authorization. Can be called synchronously.
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class AddLogMessage extends Function {
        /**
         * Minimum verbosity level needed for the message to be logged, 0-1023.
         */
        public int verbosityLevel;
        /**
         * Text of a message to log.
         */
        public String text;

        /**
         * Default constructor for a function, which adds a message to tonlib internal log. This is an offline method. Can be called before authorization. Can be called synchronously.
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public AddLogMessage() {
        }

        /**
         * Creates a function, which adds a message to tonlib internal log. This is an offline method. Can be called before authorization. Can be called synchronously.
         *
         * <p> Returns {@link Ok Ok} </p>
         *
         * @param verbosityLevel Minimum verbosity level needed for the message to be logged, 0-1023.
         * @param text Text of a message to log.
         */
        public AddLogMessage(int verbosityLevel, String text) {
            this.verbosityLevel = verbosityLevel;
            this.text = text;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1597427692;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Key Key} </p>
     */
    public static class ChangeLocalPassword extends Function {
        public InputKey inputKey;
        public byte[] newLocalPassword;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Key Key} </p>
         */
        public ChangeLocalPassword() {
        }

        public ChangeLocalPassword(InputKey inputKey, byte[] newLocalPassword) {
            this.inputKey = inputKey;
            this.newLocalPassword = newLocalPassword;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -401590337;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class Close extends Function {

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public Close() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1187782273;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Key Key} </p>
     */
    public static class CreateNewKey extends Function {
        public byte[] localPassword;
        public byte[] mnemonicPassword;
        public byte[] randomExtraSeed;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Key Key} </p>
         */
        public CreateNewKey() {
        }

        public CreateNewKey(byte[] localPassword, byte[] mnemonicPassword, byte[] randomExtraSeed) {
            this.localPassword = localPassword;
            this.mnemonicPassword = mnemonicPassword;
            this.randomExtraSeed = randomExtraSeed;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1861385712;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Data Data} </p>
     */
    public static class Decrypt extends Function {
        public byte[] encryptedData;
        public byte[] secret;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Data Data} </p>
         */
        public Decrypt() {
        }

        public Decrypt(byte[] encryptedData, byte[] secret) {
            this.encryptedData = encryptedData;
            this.secret = secret;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 357991854;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class DeleteAllKeys extends Function {

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public DeleteAllKeys() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1608776483;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class DeleteKey extends Function {
        public Key key;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public DeleteKey() {
        }

        public DeleteKey(Key key) {
            this.key = key;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1579595571;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Data Data} </p>
     */
    public static class Encrypt extends Function {
        public byte[] decryptedData;
        public byte[] secret;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Data Data} </p>
         */
        public Encrypt() {
        }

        public Encrypt(byte[] decryptedData, byte[] secret) {
            this.decryptedData = decryptedData;
            this.secret = secret;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1821422820;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link ExportedEncryptedKey ExportedEncryptedKey} </p>
     */
    public static class ExportEncryptedKey extends Function {
        public InputKey inputKey;
        public byte[] keyPassword;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link ExportedEncryptedKey ExportedEncryptedKey} </p>
         */
        public ExportEncryptedKey() {
        }

        public ExportEncryptedKey(InputKey inputKey, byte[] keyPassword) {
            this.inputKey = inputKey;
            this.keyPassword = keyPassword;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 218237311;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link ExportedKey ExportedKey} </p>
     */
    public static class ExportKey extends Function {
        public InputKey inputKey;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link ExportedKey ExportedKey} </p>
         */
        public ExportKey() {
        }

        public ExportKey(InputKey inputKey) {
            this.inputKey = inputKey;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1622353549;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link ExportedPemKey ExportedPemKey} </p>
     */
    public static class ExportPemKey extends Function {
        public InputKey inputKey;
        public byte[] keyPassword;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link ExportedPemKey ExportedPemKey} </p>
         */
        public ExportPemKey() {
        }

        public ExportPemKey(InputKey inputKey, byte[] keyPassword) {
            this.inputKey = inputKey;
            this.keyPassword = keyPassword;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -643259462;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link QueryInfo QueryInfo} </p>
     */
    public static class GenericCreateSendGramsQuery extends Function {
        public InputKey privateKey;
        public AccountAddress source;
        public AccountAddress destination;
        public long amount;
        public int timeout;
        public boolean allowSendToUninited;
        public byte[] message;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link QueryInfo QueryInfo} </p>
         */
        public GenericCreateSendGramsQuery() {
        }

        public GenericCreateSendGramsQuery(InputKey privateKey, AccountAddress source, AccountAddress destination, long amount, int timeout, boolean allowSendToUninited, byte[] message) {
            this.privateKey = privateKey;
            this.source = source;
            this.destination = destination;
            this.amount = amount;
            this.timeout = timeout;
            this.allowSendToUninited = allowSendToUninited;
            this.message = message;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 208206338;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link GenericAccountState GenericAccountState} </p>
     */
    public static class GenericGetAccountState extends Function {
        public AccountAddress accountAddress;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link GenericAccountState GenericAccountState} </p>
         */
        public GenericGetAccountState() {
        }

        public GenericGetAccountState(AccountAddress accountAddress) {
            this.accountAddress = accountAddress;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -657000446;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link SendGramsResult SendGramsResult} </p>
     */
    public static class GenericSendGrams extends Function {
        public InputKey privateKey;
        public AccountAddress source;
        public AccountAddress destination;
        public long amount;
        public int timeout;
        public boolean allowSendToUninited;
        public byte[] message;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link SendGramsResult SendGramsResult} </p>
         */
        public GenericSendGrams() {
        }

        public GenericSendGrams(InputKey privateKey, AccountAddress source, AccountAddress destination, long amount, int timeout, boolean allowSendToUninited, byte[] message) {
            this.privateKey = privateKey;
            this.source = source;
            this.destination = destination;
            this.amount = amount;
            this.timeout = timeout;
            this.allowSendToUninited = allowSendToUninited;
            this.message = message;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -553513162;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Bip39Hints Bip39Hints} </p>
     */
    public static class GetBip39Hints extends Function {
        public String prefix;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Bip39Hints Bip39Hints} </p>
         */
        public GetBip39Hints() {
        }

        public GetBip39Hints(String prefix) {
            this.prefix = prefix;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1889640982;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     * Returns information about currently used log stream for internal logging of tonlib. This is an offline method. Can be called before authorization. Can be called synchronously.
     *
     * <p> Returns {@link LogStream LogStream} </p>
     */
    public static class GetLogStream extends Function {

        /**
         * Default constructor for a function, which returns information about currently used log stream for internal logging of tonlib. This is an offline method. Can be called before authorization. Can be called synchronously.
         *
         * <p> Returns {@link LogStream LogStream} </p>
         */
        public GetLogStream() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1167608667;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     * Returns current verbosity level for a specified tonlib internal log tag. This is an offline method. Can be called before authorization. Can be called synchronously.
     *
     * <p> Returns {@link LogVerbosityLevel LogVerbosityLevel} </p>
     */
    public static class GetLogTagVerbosityLevel extends Function {
        /**
         * Logging tag to change verbosity level.
         */
        public String tag;

        /**
         * Default constructor for a function, which returns current verbosity level for a specified tonlib internal log tag. This is an offline method. Can be called before authorization. Can be called synchronously.
         *
         * <p> Returns {@link LogVerbosityLevel LogVerbosityLevel} </p>
         */
        public GetLogTagVerbosityLevel() {
        }

        /**
         * Creates a function, which returns current verbosity level for a specified tonlib internal log tag. This is an offline method. Can be called before authorization. Can be called synchronously.
         *
         * <p> Returns {@link LogVerbosityLevel LogVerbosityLevel} </p>
         *
         * @param tag Logging tag to change verbosity level.
         */
        public GetLogTagVerbosityLevel(String tag) {
            this.tag = tag;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 951004547;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     * Returns list of available tonlib internal log tags, for example, [&quot;actor&quot;, &quot;binlog&quot;, &quot;connections&quot;, &quot;notifications&quot;, &quot;proxy&quot;]. This is an offline method. Can be called before authorization. Can be called synchronously.
     *
     * <p> Returns {@link LogTags LogTags} </p>
     */
    public static class GetLogTags extends Function {

        /**
         * Default constructor for a function, which returns list of available tonlib internal log tags, for example, [&quot;actor&quot;, &quot;binlog&quot;, &quot;connections&quot;, &quot;notifications&quot;, &quot;proxy&quot;]. This is an offline method. Can be called before authorization. Can be called synchronously.
         *
         * <p> Returns {@link LogTags LogTags} </p>
         */
        public GetLogTags() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -254449190;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     * Returns current verbosity level of the internal logging of tonlib. This is an offline method. Can be called before authorization. Can be called synchronously.
     *
     * <p> Returns {@link LogVerbosityLevel LogVerbosityLevel} </p>
     */
    public static class GetLogVerbosityLevel extends Function {

        /**
         * Default constructor for a function, which returns current verbosity level of the internal logging of tonlib. This is an offline method. Can be called before authorization. Can be called synchronously.
         *
         * <p> Returns {@link LogVerbosityLevel LogVerbosityLevel} </p>
         */
        public GetLogVerbosityLevel() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 594057956;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Key Key} </p>
     */
    public static class ImportEncryptedKey extends Function {
        public byte[] localPassword;
        public byte[] keyPassword;
        public ExportedEncryptedKey exportedEncryptedKey;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Key Key} </p>
         */
        public ImportEncryptedKey() {
        }

        public ImportEncryptedKey(byte[] localPassword, byte[] keyPassword, ExportedEncryptedKey exportedEncryptedKey) {
            this.localPassword = localPassword;
            this.keyPassword = keyPassword;
            this.exportedEncryptedKey = exportedEncryptedKey;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 656724958;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Key Key} </p>
     */
    public static class ImportKey extends Function {
        public byte[] localPassword;
        public byte[] mnemonicPassword;
        public ExportedKey exportedKey;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Key Key} </p>
         */
        public ImportKey() {
        }

        public ImportKey(byte[] localPassword, byte[] mnemonicPassword, ExportedKey exportedKey) {
            this.localPassword = localPassword;
            this.mnemonicPassword = mnemonicPassword;
            this.exportedKey = exportedKey;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1607900903;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Key Key} </p>
     */
    public static class ImportPemKey extends Function {
        public byte[] localPassword;
        public byte[] keyPassword;
        public ExportedPemKey exportedKey;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Key Key} </p>
         */
        public ImportPemKey() {
        }

        public ImportPemKey(byte[] localPassword, byte[] keyPassword, ExportedPemKey exportedKey) {
            this.localPassword = localPassword;
            this.keyPassword = keyPassword;
            this.exportedKey = exportedKey;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 76385617;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class Init extends Function {
        public Options options;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public Init() {
        }

        public Init(Options options) {
            this.options = options;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -2014661877;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Data Data} </p>
     */
    public static class Kdf extends Function {
        public byte[] password;
        public byte[] salt;
        public int iterations;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Data Data} </p>
         */
        public Kdf() {
        }

        public Kdf(byte[] password, byte[] salt, int iterations) {
            this.password = password;
            this.salt = salt;
            this.iterations = iterations;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1667861635;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link LiteServerInfo LiteServerInfo} </p>
     */
    public static class LiteServerGetInfo extends Function {

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link LiteServerInfo LiteServerInfo} </p>
         */
        public LiteServerGetInfo() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1435327470;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class OnLiteServerQueryError extends Function {
        public long id;
        public Error error;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public OnLiteServerQueryError() {
        }

        public OnLiteServerQueryError(long id, Error error) {
            this.id = id;
            this.error = error;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -677427533;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class OnLiteServerQueryResult extends Function {
        public long id;
        public byte[] bytes;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public OnLiteServerQueryResult() {
        }

        public OnLiteServerQueryResult(long id, byte[] bytes) {
            this.id = id;
            this.bytes = bytes;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 2056444510;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class OptionsSetConfig extends Function {
        public Config config;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public OptionsSetConfig() {
        }

        public OptionsSetConfig(Config config) {
            this.config = config;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 646497241;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link OptionsConfigInfo OptionsConfigInfo} </p>
     */
    public static class OptionsValidateConfig extends Function {
        public Config config;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link OptionsConfigInfo OptionsConfigInfo} </p>
         */
        public OptionsValidateConfig() {
        }

        public OptionsValidateConfig(Config config) {
            this.config = config;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -346965447;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link AccountAddress AccountAddress} </p>
     */
    public static class PackAccountAddress extends Function {
        public UnpackedAccountAddress accountAddress;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link AccountAddress AccountAddress} </p>
         */
        public PackAccountAddress() {
        }

        public PackAccountAddress(UnpackedAccountAddress accountAddress) {
            this.accountAddress = accountAddress;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1388561940;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link QueryFees QueryFees} </p>
     */
    public static class QueryEstimateFees extends Function {
        public long id;
        public boolean ignoreChksig;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link QueryFees QueryFees} </p>
         */
        public QueryEstimateFees() {
        }

        public QueryEstimateFees(long id, boolean ignoreChksig) {
            this.id = id;
            this.ignoreChksig = ignoreChksig;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -957002175;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class QueryForget extends Function {
        public long id;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public QueryForget() {
        }

        public QueryForget(long id) {
            this.id = id;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1211985313;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link QueryInfo QueryInfo} </p>
     */
    public static class QueryGetInfo extends Function {
        public long id;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link QueryInfo QueryInfo} </p>
         */
        public QueryGetInfo() {
        }

        public QueryGetInfo(long id) {
            this.id = id;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -799333669;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class QuerySend extends Function {
        public long id;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public QuerySend() {
        }

        public QuerySend(long id) {
            this.id = id;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 925242739;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class RawCreateAndSendMessage extends Function {
        public AccountAddress destination;
        public byte[] initialAccountState;
        public byte[] data;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public RawCreateAndSendMessage() {
        }

        public RawCreateAndSendMessage(AccountAddress destination, byte[] initialAccountState, byte[] data) {
            this.destination = destination;
            this.initialAccountState = initialAccountState;
            this.data = data;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -772224603;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link QueryInfo QueryInfo} </p>
     */
    public static class RawCreateQuery extends Function {
        public AccountAddress destination;
        public byte[] initCode;
        public byte[] initData;
        public byte[] body;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link QueryInfo QueryInfo} </p>
         */
        public RawCreateQuery() {
        }

        public RawCreateQuery(AccountAddress destination, byte[] initCode, byte[] initData, byte[] body) {
            this.destination = destination;
            this.initCode = initCode;
            this.initData = initData;
            this.body = body;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1928557909;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link AccountAddress AccountAddress} </p>
     */
    public static class RawGetAccountAddress extends Function {
        public RawInitialAccountState inititalAccountState;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link AccountAddress AccountAddress} </p>
         */
        public RawGetAccountAddress() {
        }

        public RawGetAccountAddress(RawInitialAccountState inititalAccountState) {
            this.inititalAccountState = inititalAccountState;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -521283849;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link RawAccountState RawAccountState} </p>
     */
    public static class RawGetAccountState extends Function {
        public AccountAddress accountAddress;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link RawAccountState RawAccountState} </p>
         */
        public RawGetAccountState() {
        }

        public RawGetAccountState(AccountAddress accountAddress) {
            this.accountAddress = accountAddress;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 663706721;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link RawTransactions RawTransactions} </p>
     */
    public static class RawGetTransactions extends Function {
        public AccountAddress accountAddress;
        public InternalTransactionId fromTransactionId;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link RawTransactions RawTransactions} </p>
         */
        public RawGetTransactions() {
        }

        public RawGetTransactions(AccountAddress accountAddress, InternalTransactionId fromTransactionId) {
            this.accountAddress = accountAddress;
            this.fromTransactionId = fromTransactionId;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 935377269;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class RawSendMessage extends Function {
        public byte[] body;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public RawSendMessage() {
        }

        public RawSendMessage(byte[] body) {
            this.body = body;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1789427488;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class RunTests extends Function {
        public String dir;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public RunTests() {
        }

        public RunTests(String dir) {
            this.dir = dir;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -2039925427;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     * Sets new log stream for internal logging of tonlib. This is an offline method. Can be called before authorization. Can be called synchronously.
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class SetLogStream extends Function {
        /**
         * New log stream.
         */
        public LogStream logStream;

        /**
         * Default constructor for a function, which sets new log stream for internal logging of tonlib. This is an offline method. Can be called before authorization. Can be called synchronously.
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public SetLogStream() {
        }

        /**
         * Creates a function, which sets new log stream for internal logging of tonlib. This is an offline method. Can be called before authorization. Can be called synchronously.
         *
         * <p> Returns {@link Ok Ok} </p>
         *
         * @param logStream New log stream.
         */
        public SetLogStream(LogStream logStream) {
            this.logStream = logStream;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1364199535;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     * Sets the verbosity level for a specified tonlib internal log tag. This is an offline method. Can be called before authorization. Can be called synchronously.
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class SetLogTagVerbosityLevel extends Function {
        /**
         * Logging tag to change verbosity level.
         */
        public String tag;
        /**
         * New verbosity level; 1-1024.
         */
        public int newVerbosityLevel;

        /**
         * Default constructor for a function, which sets the verbosity level for a specified tonlib internal log tag. This is an offline method. Can be called before authorization. Can be called synchronously.
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public SetLogTagVerbosityLevel() {
        }

        /**
         * Creates a function, which sets the verbosity level for a specified tonlib internal log tag. This is an offline method. Can be called before authorization. Can be called synchronously.
         *
         * <p> Returns {@link Ok Ok} </p>
         *
         * @param tag Logging tag to change verbosity level.
         * @param newVerbosityLevel New verbosity level; 1-1024.
         */
        public SetLogTagVerbosityLevel(String tag, int newVerbosityLevel) {
            this.tag = tag;
            this.newVerbosityLevel = newVerbosityLevel;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -2095589738;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     * Sets the verbosity level of the internal logging of tonlib. This is an offline method. Can be called before authorization. Can be called synchronously.
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class SetLogVerbosityLevel extends Function {
        /**
         * New value of the verbosity level for logging. Value 0 corresponds to fatal errors, value 1 corresponds to errors, value 2 corresponds to warnings and debug warnings, value 3 corresponds to informational, value 4 corresponds to debug, value 5 corresponds to verbose debug, value greater than 5 and up to 1023 can be used to enable even more logging.
         */
        public int newVerbosityLevel;

        /**
         * Default constructor for a function, which sets the verbosity level of the internal logging of tonlib. This is an offline method. Can be called before authorization. Can be called synchronously.
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public SetLogVerbosityLevel() {
        }

        /**
         * Creates a function, which sets the verbosity level of the internal logging of tonlib. This is an offline method. Can be called before authorization. Can be called synchronously.
         *
         * <p> Returns {@link Ok Ok} </p>
         *
         * @param newVerbosityLevel New value of the verbosity level for logging. Value 0 corresponds to fatal errors, value 1 corresponds to errors, value 2 corresponds to warnings and debug warnings, value 3 corresponds to informational, value 4 corresponds to debug, value 5 corresponds to verbose debug, value greater than 5 and up to 1023 can be used to enable even more logging.
         */
        public SetLogVerbosityLevel(int newVerbosityLevel) {
            this.newVerbosityLevel = newVerbosityLevel;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -303429678;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link TvmCell TvmCell} </p>
     */
    public static class SmcGetCode extends Function {
        public long id;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link TvmCell TvmCell} </p>
         */
        public SmcGetCode() {
        }

        public SmcGetCode(long id) {
            this.id = id;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -2115626088;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link TvmCell TvmCell} </p>
     */
    public static class SmcGetData extends Function {
        public long id;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link TvmCell TvmCell} </p>
         */
        public SmcGetData() {
        }

        public SmcGetData(long id) {
            this.id = id;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -427601079;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link TvmCell TvmCell} </p>
     */
    public static class SmcGetState extends Function {
        public long id;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link TvmCell TvmCell} </p>
         */
        public SmcGetState() {
        }

        public SmcGetState(long id) {
            this.id = id;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -214390293;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link SmcInfo SmcInfo} </p>
     */
    public static class SmcLoad extends Function {
        public AccountAddress accountAddress;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link SmcInfo SmcInfo} </p>
         */
        public SmcLoad() {
        }

        public SmcLoad(AccountAddress accountAddress) {
            this.accountAddress = accountAddress;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -903491521;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link SmcRunResult SmcRunResult} </p>
     */
    public static class SmcRunGetMethod extends Function {
        public long id;
        public SmcMethodId method;
        public TvmStackEntry[] stack;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link SmcRunResult SmcRunResult} </p>
         */
        public SmcRunGetMethod() {
        }

        public SmcRunGetMethod(long id, SmcMethodId method, TvmStackEntry[] stack) {
            this.id = id;
            this.method = method;
            this.stack = stack;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -255261270;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class Sync extends Function {

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public Sync() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1617065525;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link AccountAddress AccountAddress} </p>
     */
    public static class TestGiverGetAccountAddress extends Function {

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link AccountAddress AccountAddress} </p>
         */
        public TestGiverGetAccountAddress() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -540100768;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link TestGiverAccountState TestGiverAccountState} </p>
     */
    public static class TestGiverGetAccountState extends Function {

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link TestGiverAccountState TestGiverAccountState} </p>
         */
        public TestGiverGetAccountState() {
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 267738275;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link SendGramsResult SendGramsResult} </p>
     */
    public static class TestGiverSendGrams extends Function {
        public AccountAddress destination;
        public int seqno;
        public long amount;
        public byte[] message;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link SendGramsResult SendGramsResult} </p>
         */
        public TestGiverSendGrams() {
        }

        public TestGiverSendGrams(AccountAddress destination, int seqno, long amount, byte[] message) {
            this.destination = destination;
            this.seqno = seqno;
            this.amount = amount;
            this.message = message;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1785750375;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link AccountAddress AccountAddress} </p>
     */
    public static class TestWalletGetAccountAddress extends Function {
        public TestWalletInitialAccountState inititalAccountState;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link AccountAddress AccountAddress} </p>
         */
        public TestWalletGetAccountAddress() {
        }

        public TestWalletGetAccountAddress(TestWalletInitialAccountState inititalAccountState) {
            this.inititalAccountState = inititalAccountState;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1557748223;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link TestWalletAccountState TestWalletAccountState} </p>
     */
    public static class TestWalletGetAccountState extends Function {
        public AccountAddress accountAddress;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link TestWalletAccountState TestWalletAccountState} </p>
         */
        public TestWalletGetAccountState() {
        }

        public TestWalletGetAccountState(AccountAddress accountAddress) {
            this.accountAddress = accountAddress;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 654082364;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class TestWalletInit extends Function {
        public InputKey privateKey;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public TestWalletInit() {
        }

        public TestWalletInit(InputKey privateKey) {
            this.privateKey = privateKey;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1417409140;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link SendGramsResult SendGramsResult} </p>
     */
    public static class TestWalletSendGrams extends Function {
        public InputKey privateKey;
        public AccountAddress destination;
        public int seqno;
        public long amount;
        public byte[] message;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link SendGramsResult SendGramsResult} </p>
         */
        public TestWalletSendGrams() {
        }

        public TestWalletSendGrams(InputKey privateKey, AccountAddress destination, int seqno, long amount, byte[] message) {
            this.privateKey = privateKey;
            this.destination = destination;
            this.seqno = seqno;
            this.amount = amount;
            this.message = message;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 573748322;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link UnpackedAccountAddress UnpackedAccountAddress} </p>
     */
    public static class UnpackAccountAddress extends Function {
        public String accountAddress;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link UnpackedAccountAddress UnpackedAccountAddress} </p>
         */
        public UnpackAccountAddress() {
        }

        public UnpackAccountAddress(String accountAddress) {
            this.accountAddress = accountAddress;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -682459063;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link AccountAddress AccountAddress} </p>
     */
    public static class WalletGetAccountAddress extends Function {
        public WalletInitialAccountState inititalAccountState;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link AccountAddress AccountAddress} </p>
         */
        public WalletGetAccountAddress() {
        }

        public WalletGetAccountAddress(WalletInitialAccountState inititalAccountState) {
            this.inititalAccountState = inititalAccountState;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -1004103180;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link WalletAccountState WalletAccountState} </p>
     */
    public static class WalletGetAccountState extends Function {
        public AccountAddress accountAddress;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link WalletAccountState WalletAccountState} </p>
         */
        public WalletGetAccountState() {
        }

        public WalletGetAccountState(AccountAddress accountAddress) {
            this.accountAddress = accountAddress;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 462294850;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link Ok Ok} </p>
     */
    public static class WalletInit extends Function {
        public InputKey privateKey;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link Ok Ok} </p>
         */
        public WalletInit() {
        }

        public WalletInit(InputKey privateKey) {
            this.privateKey = privateKey;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = -395706309;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link SendGramsResult SendGramsResult} </p>
     */
    public static class WalletSendGrams extends Function {
        public InputKey privateKey;
        public AccountAddress destination;
        public int seqno;
        public long validUntil;
        public long amount;
        public byte[] message;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link SendGramsResult SendGramsResult} </p>
         */
        public WalletSendGrams() {
        }

        public WalletSendGrams(InputKey privateKey, AccountAddress destination, int seqno, long validUntil, long amount, byte[] message) {
            this.privateKey = privateKey;
            this.destination = destination;
            this.seqno = seqno;
            this.validUntil = validUntil;
            this.amount = amount;
            this.message = message;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 297317621;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

    /**
     *
     *
     * <p> Returns {@link AccountAddress AccountAddress} </p>
     */
    public static class WalletV3GetAccountAddress extends Function {
        public WalletV3InitialAccountState inititalAccountState;

        /**
         * Default constructor for a function, which
         *
         * <p> Returns {@link AccountAddress AccountAddress} </p>
         */
        public WalletV3GetAccountAddress() {
        }

        public WalletV3GetAccountAddress(WalletV3InitialAccountState inititalAccountState) {
            this.inititalAccountState = inititalAccountState;
        }

        /**
         * Identifier uniquely determining type of the object.
         */
        public static final int CONSTRUCTOR = 1011655671;

        /**
         * @return this.CONSTRUCTOR
         */
        @Override
        public int getConstructor() {
            return CONSTRUCTOR;
        }
    }

}
