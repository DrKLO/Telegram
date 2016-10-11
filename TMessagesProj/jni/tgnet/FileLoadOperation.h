/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2016.
 */

#ifndef FILELOADOPERATION_H
#define FILELOADOPERATION_H

#include <vector>
#include "Defines.h"

#ifdef ANDROID
#include <jni.h>
#endif

class TL_upload_file;
class InputFileLocation;
class ByteArray;
class FileLocation;

class FileLoadOperation {

public:
    FileLoadOperation(int32_t dc_id, int64_t id, int64_t volume_id, int64_t access_hash, int32_t local_id, uint8_t *encKey, uint8_t *encIv, std::string extension, int32_t version, int32_t size, std::string dest, std::string temp);
    ~FileLoadOperation();

    void start();
    void cancel();
    void setDelegate(onFinishedFunc onFinished, onFailedFunc onFailed, onProgressChangedFunc onProgressChanged);

#ifdef ANDROID
    jobject ptr1 = nullptr;
#endif

private:

    class RequestInfo {

    public:
        int32_t requestToken = 0;
        int32_t offset = 0;
        NativeByteBuffer *bytes = nullptr;

        ~RequestInfo();
    };

    void cleanup();
    void onFinishLoadingFile();
    void startDownloadRequest();
    void processRequestResult(RequestInfo *requestInfo, TL_error *error, bool next);
    void onFailedLoadingFile(int reason);

    int32_t datacenter_id;
    std::unique_ptr<InputFileLocation> location;
    FileLoadState state = FileLoadStateIdle;
    int32_t downloadedBytes = 0;
    int32_t totalBytesCount = 0;
    int32_t bytesCountPadding = 0;
    std::unique_ptr<ByteArray> key;
    std::unique_ptr<ByteArray> iv;
    int32_t currentDownloadChunkSize = 0;
    uint32_t currentMaxDownloadRequests = 0;
    int32_t requestsCount = 0;

    int32_t nextDownloadOffset = 0;
    std::vector<std::unique_ptr<RequestInfo>> requestInfos;
    std::vector<std::unique_ptr<RequestInfo>> delayedRequestInfos;

    std::string ext;

    std::string filePath;
    std::string tempFilePath;
    std::string tempFileIvPath;

    FILE *tempFile = nullptr;
    FILE *tempIvFile = nullptr;

    std::string destPath;
    std::string tempPath;

    bool isForceRequest = false;

    onFinishedFunc onFinishedCallback = nullptr;
    onFailedFunc onFailedCallback = nullptr;
    onProgressChangedFunc onProgressChangedCallback = nullptr;
};

#endif
