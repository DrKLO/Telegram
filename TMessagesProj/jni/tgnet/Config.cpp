/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#include <sys/stat.h>
#include <unistd.h>
#include <errno.h>
#include <cstring>
#include "Config.h"
#include "ConnectionsManager.h"
#include "FileLog.h"
#include "BuffersStorage.h"

Config::Config(int32_t instance, std::string fileName) {
    instanceNum = instance;
    configPath = ConnectionsManager::getInstance(instanceNum).currentConfigPath + fileName;
    backupPath = configPath + ".bak";
    FILE *backup = fopen(backupPath.c_str(), "rb");
    if (backup != nullptr) {
        DEBUG_D("Config(%p, %s) backup file found %s", this, configPath.c_str(), backupPath.c_str());
        fclose(backup);
        remove(configPath.c_str());
        rename(backupPath.c_str(), configPath.c_str());
    }
}

NativeByteBuffer *Config::readConfig() {
    NativeByteBuffer *buffer = nullptr;
    FILE *file = fopen(configPath.c_str(), "rb");
    if (file != nullptr) {
        fseek(file, 0, SEEK_END);
        long fileSize = ftell(file);
        if (fseek(file, 0, SEEK_SET)) {
            DEBUG_E("Config(%p, %s) failed fseek to begin, reopen it", this, configPath.c_str());
            fclose(file);
            file = fopen(configPath.c_str(), "rb");
        }
        uint32_t size = 0;
        size_t bytesRead = fread(&size, sizeof(uint32_t), 1, file);
        DEBUG_D("Config(%p, %s) load, size = %u, fileSize = %u", this, configPath.c_str(), size, (uint32_t) fileSize);
        if (bytesRead > 0 && size > 0 && (int32_t) size < fileSize) {
            buffer = BuffersStorage::getInstance().getFreeBuffer(size);
            if (fread(buffer->bytes(), sizeof(uint8_t), size, file) != size) {
                buffer->reuse();
                buffer = nullptr;
            }
        }
        fclose(file);
    }
    return buffer;
}

void Config::writeConfig(NativeByteBuffer *buffer) {
    DEBUG_D("Config(%p, %s) start write config", this, configPath.c_str());
    FILE *file = fopen(configPath.c_str(), "rb");
    FILE *backup = fopen(backupPath.c_str(), "rb");
    bool error = false;
    bool hasBackupFile = false;
    if (file != nullptr) {
        if (backup == nullptr) {
            fclose(file);
            if (rename(configPath.c_str(), backupPath.c_str()) != 0) {
                DEBUG_E("Config(%p) unable to rename file %s to backup file %s", this, configPath.c_str(), backupPath.c_str());
                error = true;
            } else {
                hasBackupFile = true;
            }
        } else {
            fclose(file);
            fclose(backup);
            remove(configPath.c_str());
        }
    }
    if (error) {
        return;
    }
    file = fopen(configPath.c_str(), "wb");
    if (chmod(configPath.c_str(), 0660)) {
        DEBUG_E("Config(%p, %s) chmod failed", this, configPath.c_str());
    }
    if (file == nullptr) {
        DEBUG_E("Config(%p, %s) unable to open file for writing", this, configPath.c_str());
        return;
    }
    uint32_t size = buffer->position();
    if (fwrite(&size, sizeof(uint32_t), 1, file) == 1) {
        if (fwrite(buffer->bytes(), sizeof(uint8_t), size, file) != size) {
            DEBUG_E("Config(%p, %s) failed to write config data to file", this, configPath.c_str());
            error = true;
        }
    } else {
        DEBUG_E("Config(%p, %s) failed to write config size to file", this, configPath.c_str());
        error = true;
    }
    if (fflush(file)) {
        DEBUG_E("Config(%p, %s) fflush failed", this, configPath.c_str());
        error = true;
    }
    int fd = fileno(file);
    if (fd == -1) {
        DEBUG_E("Config(%p, %s) fileno failed", this, configPath.c_str());
        error = true;
    } else {
        DEBUG_D("Config(%p, %s) fileno = %d", this, configPath.c_str(), fd);
    }
    if (fd != -1 && fsync(fd) == -1) {
        DEBUG_E("Config(%p, %s) fsync failed", this, configPath.c_str());
        error = true;
    }
    if (fclose(file)) {
        DEBUG_E("Config(%p, %s) fclose failed", this, configPath.c_str());
        error = true;
    }
    if (error) {
        DEBUG_E("Config(%p, %s) failed to write config", this, configPath.c_str());
        if (remove(configPath.c_str())) {
            DEBUG_E("Config(%p, %s) remove config failed", this, configPath.c_str());
        }
    } else {
        if (hasBackupFile && remove(backupPath.c_str())) {
            DEBUG_E("Config(%p, %s) remove backup failed, %s", this, backupPath.c_str(), strerror(errno));
        }
    }
    if (!error) {
        DEBUG_D("Config(%p, %s) config write ok", this, configPath.c_str());
    }
}
