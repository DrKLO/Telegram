/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2016.
 */

#include "FileLoadOperation.h"
#include <algorithm>
#include "ApiScheme.h"
#include "ByteArray.h"
#include "MTProtoScheme.h"
#include "FileLog.h"
#include "ConnectionsManager.h"
#include "NativeByteBuffer.h"
#include "Datacenter.h"

FileLoadOperation::FileLoadOperation(int32_t dc_id, int64_t id, int64_t volume_id, int64_t access_hash, int32_t local_id, uint8_t *encKey, uint8_t *encIv, std::string extension, int32_t version, int32_t size, std::string dest, std::string temp) {
    if (!dest.empty() && dest.find_last_of('/') != dest.size() - 1) {
        dest += "/";
    }
    if (!temp.empty() && temp.find_last_of('/') != temp.size() - 1) {
        temp += "/";
    }
    if (encKey != nullptr) {
        location = std::unique_ptr<InputFileLocation>(new TL_inputEncryptedFileLocation());
        location->id = id;
        location->access_hash = access_hash;
        location->volume_id = volume_id;
        location->local_id = local_id;
        key = std::unique_ptr<ByteArray>(new ByteArray(encKey, 32));
        iv = std::unique_ptr<ByteArray>(new ByteArray(encIv, 32));
    } else {
        if (volume_id != 0) {
            location = std::unique_ptr<InputFileLocation>(new TL_inputFileLocation());
            location->volume_id = volume_id;
            location->local_id = local_id;
            location->secret = access_hash;
        } else {
            location = std::unique_ptr<InputFileLocation>(new TL_inputDocumentFileLocation());
            location->id = id;
            location->access_hash = access_hash;
            location->version = version;
        }
    }
    destPath = dest;
    tempPath = temp;
    datacenter_id = dc_id;
    totalBytesCount = size;
    ext = extension;
    if (key != nullptr) {
        if (totalBytesCount % 16 != 0) {
            bytesCountPadding = 16 - totalBytesCount % 16;
            totalBytesCount += bytesCountPadding;
        }
    }
}

FileLoadOperation::~FileLoadOperation() {
#ifdef ANDROID
    if (ptr1 != nullptr) {
        jniEnv->DeleteGlobalRef(ptr1);
        ptr1 = nullptr;
    }
#endif
}

void FileLoadOperation::start() {
    ConnectionsManager::getInstance().scheduleTask([&] {
        if (state != FileLoadStateIdle) {
            return;
        }
        currentDownloadChunkSize = totalBytesCount >= DOWNLOAD_BIG_FILE_MIN_SIZE ? DOWNLOAD_CHUNK_BIG_SIZE : DOWNLOAD_CHUNK_SIZE;
        currentMaxDownloadRequests = totalBytesCount >= DOWNLOAD_BIG_FILE_MIN_SIZE ? DOWNLOAD_MAX_BIG_REQUESTS : DOWNLOAD_MAX_REQUESTS;
        state = FileLoadStateDownloading;
        if (location == nullptr) {
            onFailedLoadingFile(FileLoadFailReasonError);
            return;
        }
        std::string prefix;
        if (location->volume_id != 0 && location->local_id != 0) {
            if (datacenter_id == INT_MIN || location->volume_id == INT_MIN || datacenter_id == 0) {
                onFailedLoadingFile(FileLoadFailReasonError);
                return;
            }
            prefix = to_string_uint64(location->volume_id) + "_" + to_string_int32(location->local_id);
        } else {
            if (datacenter_id == 0 || location->id == 0) {
                onFailedLoadingFile(FileLoadFailReasonError);
                return;
            }
            prefix = to_string_int32(datacenter_id) + "_" + to_string_uint64(location->id);
        }
        filePath = destPath + prefix + "." + ext;
        tempFilePath = tempPath + prefix + ".temp";
        if (key != nullptr) {
            tempFileIvPath = tempPath + prefix + ".iv";
        }

        FILE *destFile = fopen(filePath.c_str(), "rb");
        if (destFile != nullptr) {
            long len = ftell(destFile);
            if (totalBytesCount != 0 && totalBytesCount != len) {
                fclose(destFile);
                destFile = nullptr;
                remove(filePath.c_str());
            }
        }

        if (destFile == nullptr) {
            tempFile = fopen(tempFilePath.c_str(), "r+b");
            if (tempFile != nullptr) {
                if (!fseek(tempFile, 0, SEEK_END) && (downloadedBytes = ftell(tempFile)) != -1L) {
                    nextDownloadOffset = downloadedBytes = downloadedBytes / currentDownloadChunkSize * currentDownloadChunkSize;
                } else {
                    fclose(tempFile);
                    tempFile = nullptr;
                }
            }

            if (key != nullptr) {
                if (tempFile != nullptr) {
                    tempIvFile = fopen(tempFileIvPath.c_str(), "r+b");
                    if (tempIvFile != nullptr) {
                        if (fread(iv->bytes, sizeof(uint8_t), 32, tempIvFile) != 32) {
                            fclose(tempIvFile);
                            tempIvFile = nullptr;
                        }
                    }
                }
                if (tempIvFile == nullptr) {
                    tempIvFile = fopen(tempFileIvPath.c_str(), "w+b");
                    nextDownloadOffset = downloadedBytes = 0;
                    if (tempIvFile == nullptr) {
                        onFailedLoadingFile(FileLoadFailReasonError);
                        return;
                    }
                }
            }

            if (tempFile != nullptr) {
                if (downloadedBytes != 0) {
                    if (!fseek(tempFile, downloadedBytes, SEEK_SET)) {
                        DEBUG_D("resume loading file to temp = %s final = %s from %d", tempFilePath.c_str(), filePath.c_str(), nextDownloadOffset);
                    } else {
                        fclose(tempFile);
                        tempFile = nullptr;
                    }
                }
            }
            if (tempFile == nullptr) {
                nextDownloadOffset = downloadedBytes = 0;
                tempFile = fopen(tempFilePath.c_str(), "w+b");
                if (tempFile == nullptr) {
                    onFailedLoadingFile(FileLoadFailReasonError);
                    return;
                }
                DEBUG_D("start loading file to temp = %s final = %s", tempFilePath.c_str(), filePath.c_str());
            }
            if (totalBytesCount != 0 && downloadedBytes == totalBytesCount) {
                onFinishLoadingFile();
            } else {
                startDownloadRequest();
            }
        } else {
            fclose(destFile);
            onFinishLoadingFile();
        }
    });
}

/*void setForceRequest(boolean forceRequest) { TODO
    isForceRequest = forceRequest;
}

boolean isForceRequest() {
    return isForceRequest;
}
*/

void FileLoadOperation::cancel() {
    ConnectionsManager::getInstance().scheduleTask([&] {
        if (state == FileLoadStateFinished || state == FileLoadStateFailed) {
            return;
        }
        onFailedLoadingFile(FileLoadFailReasonCanceled);
    });
}

void FileLoadOperation::setDelegate(onFinishedFunc onFinished, onFailedFunc onFailed, onProgressChangedFunc onProgressChanged) {
    onFinishedCallback = onFinished;
    onFailedCallback = onFailed;
    onProgressChangedCallback = onProgressChanged;
}

void FileLoadOperation::cleanup() {
    ConnectionsManager::getInstance().scheduleTask([&] {
        if (tempFile != nullptr) {
            fclose(tempFile);
            tempFile = nullptr;
        }
        if (tempIvFile != nullptr) {
            fclose(tempIvFile);
            tempIvFile = nullptr;
        }
        for (size_t a = 0; a < requestInfos.size(); a++) {
            if (requestInfos[a] != nullptr && requestInfos[a]->requestToken != 0) {
                ConnectionsManager::getInstance().cancelRequestInternal(requestInfos[a]->requestToken, true, false);
            }
        }
        requestInfos.clear();
        delayedRequestInfos.clear();
        delete this;
    });
}

void FileLoadOperation::onFinishLoadingFile() {
    if (state != FileLoadStateDownloading) {
        return;
    }
    state = FileLoadStateFinished;
    if (tempIvFile != nullptr) {
        fclose(tempIvFile);
        tempIvFile = nullptr;
        remove(tempFileIvPath.c_str());
    }
    if (tempFile != nullptr) {
        fclose(tempFile);
        tempFile = nullptr;
        if (rename(tempFilePath.c_str(), filePath.c_str())) {
            DEBUG_E("unable to rename temp = %s to final = %s", tempFilePath.c_str(), filePath.c_str());
            filePath = tempFilePath;
        }
    }
    DEBUG_D("finished downloading file %s", filePath.c_str());
    if (onFinishedCallback != nullptr) {
        onFinishedCallback(filePath);
    }
    cleanup();
}

void FileLoadOperation::onFailedLoadingFile(int reason) {
    if (state == FileLoadStateFailed) {
        return;
    }
    state = FileLoadStateFailed;
    if (onFailedCallback != nullptr) {
        onFailedCallback(FileLoadFailReasonCanceled);
    }
    cleanup();
}

void FileLoadOperation::processRequestResult(RequestInfo *requestInfo, TL_error *error, bool next) {
    std::unique_ptr<RequestInfo> info;
    if (!next) {
        std::vector<std::unique_ptr<RequestInfo>>::iterator iter = std::find_if(requestInfos.begin(), requestInfos.end(), [&](std::unique_ptr<RequestInfo> &p) {
            return p.get() == requestInfo;
        });
        if (iter != requestInfos.end()) {
            info = std::move(*iter);
            requestInfos.erase(iter);
        }
    }
    if (error == nullptr) {
        if (!next && downloadedBytes != requestInfo->offset) {
            if (state == FileLoadStateDownloading) {
                delayedRequestInfos.push_back(std::move(info));
                startDownloadRequest();
            }
            return;
        }

        if (requestInfo->bytes == nullptr || requestInfo->bytes->limit() == 0) {
            onFinishLoadingFile();
            return;
        }
        int32_t currentBytesSize = requestInfo->bytes->limit();
        downloadedBytes += currentBytesSize;
        bool finishedDownloading = currentBytesSize != currentDownloadChunkSize || ((totalBytesCount == downloadedBytes || downloadedBytes % currentDownloadChunkSize != 0) && (totalBytesCount <= 0 || totalBytesCount <= downloadedBytes));

        if (key != nullptr) {
            Datacenter::aesIgeEncryption(requestInfo->bytes->bytes(), key->bytes, iv->bytes, false, true, currentBytesSize);
            if (finishedDownloading && bytesCountPadding != 0) {
                requestInfo->bytes->limit(currentBytesSize = (currentBytesSize - bytesCountPadding));
            }
        }
        if (tempFile != nullptr) {
            if (fwrite(requestInfo->bytes->bytes(), sizeof(uint8_t), currentBytesSize, tempFile) != (uint32_t) currentBytesSize) {
                onFailedLoadingFile(FileLoadFailReasonError);
                return;
            }
        }
        if (tempIvFile != nullptr) {
            if (fseek(tempIvFile, 0, SEEK_SET) || fwrite(iv->bytes, sizeof(uint8_t), 32, tempIvFile) != 32) {
                onFailedLoadingFile(FileLoadFailReasonError);
                return;
            }
        }
        if (totalBytesCount > 0 && state == FileLoadStateDownloading) {
            float progress = (float) downloadedBytes / (float) totalBytesCount;
            if (progress > 1.0f) {
                progress = 1.0f;
            }
            if (onProgressChangedCallback != nullptr) {
                onProgressChangedCallback(progress);
            }
        }

        for (std::vector<std::unique_ptr<RequestInfo>>::iterator iter = delayedRequestInfos.begin(); iter != delayedRequestInfos.end(); iter++) {
            if (downloadedBytes == (*iter)->offset) {
                info = std::move(*iter);
                delayedRequestInfos.erase(iter);
                processRequestResult(info.get(), nullptr, true);
                break;
            }
        }

        if (finishedDownloading) {
            onFinishLoadingFile();
        } else {
            startDownloadRequest();
        }
    } else {
        static std::string fileMigrate = "FILE_MIGRATE_";
        static std::string offsetInvalid = "OFFSET_INVALID";
        static std::string retryLimit = "RETRY_LIMIT";
        if (error->text.find(fileMigrate) != std::string::npos) {
            /*std::string num = error->text.substr(fileMigrate.size(), error->text.size() - fileMigrate.size());
            int32_t dcId = atoi(num.c_str());
            if (dcId <= 0) {
                onFailedLoadingFile(FileLoadFailReasonError);
            } else {
                datacenter_id = dcId;
                nextDownloadOffset = downloadedBytes = 0;
                startDownloadRequest();
            }*/
            onFailedLoadingFile(FileLoadFailReasonError);
        } else if (error->text.find(offsetInvalid) != std::string::npos) {
            if (downloadedBytes % currentDownloadChunkSize == 0) {
                onFinishLoadingFile();
            } else {
                onFailedLoadingFile(FileLoadFailReasonError);
            }
        } else if (error->text.find(retryLimit) != std::string::npos) {
            onFailedLoadingFile(FileLoadFailReasonRetryLimit);
        } else {
            onFailedLoadingFile(FileLoadFailReasonError);
        }
    }
}

void FileLoadOperation::startDownloadRequest() {
    if (state != FileLoadStateDownloading || (totalBytesCount > 0 && nextDownloadOffset >= totalBytesCount) || ((requestInfos.size() + delayedRequestInfos.size()) >= currentMaxDownloadRequests)) {
        return;
    }
    int32_t count = 1;
    if (totalBytesCount > 0) {
        count = currentMaxDownloadRequests - requestInfos.size();
    }

    for (int32_t a = 0; a < count; a++) {
        if (totalBytesCount > 0 && nextDownloadOffset >= totalBytesCount) {
            break;
        }
        bool isLast = totalBytesCount <= 0 || a == count - 1 || (totalBytesCount > 0 && (nextDownloadOffset + currentDownloadChunkSize) >= totalBytesCount);

        RequestInfo *requestInfo = new RequestInfo();
        requestInfos.push_back(std::unique_ptr<RequestInfo>(requestInfo));

        TL_upload_getFile *request = new TL_upload_getFile();
        request->location = location.get();
        requestInfo->offset = request->offset = nextDownloadOffset;
        request->limit = currentDownloadChunkSize;
        nextDownloadOffset += currentDownloadChunkSize;

        requestInfo->requestToken = ConnectionsManager::getInstance().sendRequest(request, [&, requestInfo](TLObject *response, TL_error *error, int32_t connectionType) {
            requestInfo->requestToken = 0;
            if (response != nullptr) {
                TL_upload_file *res = (TL_upload_file *) response;
                requestInfo->bytes = res->bytes;
                res->bytes = nullptr;
            }
            processRequestResult(requestInfo, error, false);
        }, nullptr, (isForceRequest ? RequestFlagForceDownload : 0) | RequestFlagFailOnServerErrors, datacenter_id, requestsCount % 2 == 0 ? ConnectionTypeDownload : (ConnectionType) (ConnectionTypeDownload | (1 << 16)), isLast);
        requestsCount++;
    }
}

FileLoadOperation::RequestInfo::~RequestInfo() {
    if (bytes != nullptr) {
        bytes->reuse();
        bytes = nullptr;
    }
}
