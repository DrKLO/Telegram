// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/files/file_proxy.h"

#include <utility>

#include "base/bind.h"
#include "base/bind_helpers.h"
#include "base/files/file.h"
#include "base/files/file_util.h"
#include "base/location.h"
#include "base/macros.h"
#include "base/task_runner.h"
#include "base/task_runner_util.h"

namespace {

void FileDeleter(base::File file) {
}

}  // namespace

namespace base {

class FileHelper {
 public:
   FileHelper(FileProxy* proxy, File file)
      : file_(std::move(file)),
        error_(File::FILE_ERROR_FAILED),
        task_runner_(proxy->task_runner()),
        proxy_(AsWeakPtr(proxy)) {
   }

   void PassFile() {
     if (proxy_)
       proxy_->SetFile(std::move(file_));
     else if (file_.IsValid())
       task_runner_->PostTask(FROM_HERE,
                              BindOnce(&FileDeleter, std::move(file_)));
   }

 protected:
  File file_;
  File::Error error_;

 private:
  scoped_refptr<TaskRunner> task_runner_;
  WeakPtr<FileProxy> proxy_;
  DISALLOW_COPY_AND_ASSIGN(FileHelper);
};

namespace {

class GenericFileHelper : public FileHelper {
 public:
  GenericFileHelper(FileProxy* proxy, File file)
      : FileHelper(proxy, std::move(file)) {
  }

  void Close() {
    file_.Close();
    error_ = File::FILE_OK;
  }

  void SetTimes(Time last_access_time, Time last_modified_time) {
    bool rv = file_.SetTimes(last_access_time, last_modified_time);
    error_ = rv ? File::FILE_OK : File::FILE_ERROR_FAILED;
  }

  void SetLength(int64_t length) {
    if (file_.SetLength(length))
      error_ = File::FILE_OK;
  }

  void Flush() {
    if (file_.Flush())
      error_ = File::FILE_OK;
  }

  void Reply(FileProxy::StatusCallback callback) {
    PassFile();
    if (!callback.is_null())
      std::move(callback).Run(error_);
  }

 private:
  DISALLOW_COPY_AND_ASSIGN(GenericFileHelper);
};

class CreateOrOpenHelper : public FileHelper {
 public:
  CreateOrOpenHelper(FileProxy* proxy, File file)
      : FileHelper(proxy, std::move(file)) {
  }

  void RunWork(const FilePath& file_path, int file_flags) {
    file_.Initialize(file_path, file_flags);
    error_ = file_.IsValid() ? File::FILE_OK : file_.error_details();
  }

  void Reply(FileProxy::StatusCallback callback) {
    DCHECK(!callback.is_null());
    PassFile();
    std::move(callback).Run(error_);
  }

 private:
  DISALLOW_COPY_AND_ASSIGN(CreateOrOpenHelper);
};

class CreateTemporaryHelper : public FileHelper {
 public:
  CreateTemporaryHelper(FileProxy* proxy, File file)
      : FileHelper(proxy, std::move(file)) {
  }

  void RunWork(uint32_t additional_file_flags) {
    // TODO(darin): file_util should have a variant of CreateTemporaryFile
    // that returns a FilePath and a File.
    if (!CreateTemporaryFile(&file_path_)) {
      // TODO(davidben): base::CreateTemporaryFile should preserve the error
      // code.
      error_ = File::FILE_ERROR_FAILED;
      return;
    }

    uint32_t file_flags = File::FLAG_WRITE | File::FLAG_TEMPORARY |
                          File::FLAG_CREATE_ALWAYS | additional_file_flags;

    file_.Initialize(file_path_, file_flags);
    if (file_.IsValid()) {
      error_ = File::FILE_OK;
    } else {
      error_ = file_.error_details();
      DeleteFile(file_path_, false);
      file_path_.clear();
    }
  }

  void Reply(FileProxy::CreateTemporaryCallback callback) {
    DCHECK(!callback.is_null());
    PassFile();
    std::move(callback).Run(error_, file_path_);
  }

 private:
  FilePath file_path_;
  DISALLOW_COPY_AND_ASSIGN(CreateTemporaryHelper);
};

class GetInfoHelper : public FileHelper {
 public:
  GetInfoHelper(FileProxy* proxy, File file)
      : FileHelper(proxy, std::move(file)) {
  }

  void RunWork() {
    if (file_.GetInfo(&file_info_))
      error_  = File::FILE_OK;
  }

  void Reply(FileProxy::GetFileInfoCallback callback) {
    PassFile();
    DCHECK(!callback.is_null());
    std::move(callback).Run(error_, file_info_);
  }

 private:
  File::Info file_info_;
  DISALLOW_COPY_AND_ASSIGN(GetInfoHelper);
};

class ReadHelper : public FileHelper {
 public:
  ReadHelper(FileProxy* proxy, File file, int bytes_to_read)
      : FileHelper(proxy, std::move(file)),
        buffer_(new char[bytes_to_read]),
        bytes_to_read_(bytes_to_read),
        bytes_read_(0) {
  }

  void RunWork(int64_t offset) {
    bytes_read_ = file_.Read(offset, buffer_.get(), bytes_to_read_);
    error_ = (bytes_read_ < 0) ? File::FILE_ERROR_FAILED : File::FILE_OK;
  }

  void Reply(FileProxy::ReadCallback callback) {
    PassFile();
    DCHECK(!callback.is_null());
    std::move(callback).Run(error_, buffer_.get(), bytes_read_);
  }

 private:
  std::unique_ptr<char[]> buffer_;
  int bytes_to_read_;
  int bytes_read_;
  DISALLOW_COPY_AND_ASSIGN(ReadHelper);
};

class WriteHelper : public FileHelper {
 public:
  WriteHelper(FileProxy* proxy,
              File file,
              const char* buffer, int bytes_to_write)
      : FileHelper(proxy, std::move(file)),
        buffer_(new char[bytes_to_write]),
        bytes_to_write_(bytes_to_write),
        bytes_written_(0) {
    memcpy(buffer_.get(), buffer, bytes_to_write);
  }

  void RunWork(int64_t offset) {
    bytes_written_ = file_.Write(offset, buffer_.get(), bytes_to_write_);
    error_ = (bytes_written_ < 0) ? File::FILE_ERROR_FAILED : File::FILE_OK;
  }

  void Reply(FileProxy::WriteCallback callback) {
    PassFile();
    if (!callback.is_null())
      std::move(callback).Run(error_, bytes_written_);
  }

 private:
  std::unique_ptr<char[]> buffer_;
  int bytes_to_write_;
  int bytes_written_;
  DISALLOW_COPY_AND_ASSIGN(WriteHelper);
};

}  // namespace

FileProxy::FileProxy(TaskRunner* task_runner) : task_runner_(task_runner) {
}

FileProxy::~FileProxy() {
  if (file_.IsValid())
    task_runner_->PostTask(FROM_HERE, BindOnce(&FileDeleter, std::move(file_)));
}

bool FileProxy::CreateOrOpen(const FilePath& file_path,
                             uint32_t file_flags,
                             StatusCallback callback) {
  DCHECK(!file_.IsValid());
  CreateOrOpenHelper* helper = new CreateOrOpenHelper(this, File());
  return task_runner_->PostTaskAndReply(
      FROM_HERE,
      BindOnce(&CreateOrOpenHelper::RunWork, Unretained(helper), file_path,
               file_flags),
      BindOnce(&CreateOrOpenHelper::Reply, Owned(helper), std::move(callback)));
}

bool FileProxy::CreateTemporary(uint32_t additional_file_flags,
                                CreateTemporaryCallback callback) {
  DCHECK(!file_.IsValid());
  CreateTemporaryHelper* helper = new CreateTemporaryHelper(this, File());
  return task_runner_->PostTaskAndReply(
      FROM_HERE,
      BindOnce(&CreateTemporaryHelper::RunWork, Unretained(helper),
               additional_file_flags),
      BindOnce(&CreateTemporaryHelper::Reply, Owned(helper),
               std::move(callback)));
}

bool FileProxy::IsValid() const {
  return file_.IsValid();
}

void FileProxy::SetFile(File file) {
  DCHECK(!file_.IsValid());
  file_ = std::move(file);
}

File FileProxy::TakeFile() {
  return std::move(file_);
}

File FileProxy::DuplicateFile() {
  return file_.Duplicate();
}

PlatformFile FileProxy::GetPlatformFile() const {
  return file_.GetPlatformFile();
}

bool FileProxy::Close(StatusCallback callback) {
  DCHECK(file_.IsValid());
  GenericFileHelper* helper = new GenericFileHelper(this, std::move(file_));
  return task_runner_->PostTaskAndReply(
      FROM_HERE, BindOnce(&GenericFileHelper::Close, Unretained(helper)),
      BindOnce(&GenericFileHelper::Reply, Owned(helper), std::move(callback)));
}

bool FileProxy::GetInfo(GetFileInfoCallback callback) {
  DCHECK(file_.IsValid());
  GetInfoHelper* helper = new GetInfoHelper(this, std::move(file_));
  return task_runner_->PostTaskAndReply(
      FROM_HERE, BindOnce(&GetInfoHelper::RunWork, Unretained(helper)),
      BindOnce(&GetInfoHelper::Reply, Owned(helper), std::move(callback)));
}

bool FileProxy::Read(int64_t offset, int bytes_to_read, ReadCallback callback) {
  DCHECK(file_.IsValid());
  if (bytes_to_read < 0)
    return false;

  ReadHelper* helper = new ReadHelper(this, std::move(file_), bytes_to_read);
  return task_runner_->PostTaskAndReply(
      FROM_HERE, BindOnce(&ReadHelper::RunWork, Unretained(helper), offset),
      BindOnce(&ReadHelper::Reply, Owned(helper), std::move(callback)));
}

bool FileProxy::Write(int64_t offset,
                      const char* buffer,
                      int bytes_to_write,
                      WriteCallback callback) {
  DCHECK(file_.IsValid());
  if (bytes_to_write <= 0 || buffer == nullptr)
    return false;

  WriteHelper* helper =
      new WriteHelper(this, std::move(file_), buffer, bytes_to_write);
  return task_runner_->PostTaskAndReply(
      FROM_HERE, BindOnce(&WriteHelper::RunWork, Unretained(helper), offset),
      BindOnce(&WriteHelper::Reply, Owned(helper), std::move(callback)));
}

bool FileProxy::SetTimes(Time last_access_time,
                         Time last_modified_time,
                         StatusCallback callback) {
  DCHECK(file_.IsValid());
  GenericFileHelper* helper = new GenericFileHelper(this, std::move(file_));
  return task_runner_->PostTaskAndReply(
      FROM_HERE,
      BindOnce(&GenericFileHelper::SetTimes, Unretained(helper),
               last_access_time, last_modified_time),
      BindOnce(&GenericFileHelper::Reply, Owned(helper), std::move(callback)));
}

bool FileProxy::SetLength(int64_t length, StatusCallback callback) {
  DCHECK(file_.IsValid());
  GenericFileHelper* helper = new GenericFileHelper(this, std::move(file_));
  return task_runner_->PostTaskAndReply(
      FROM_HERE,
      BindOnce(&GenericFileHelper::SetLength, Unretained(helper), length),
      BindOnce(&GenericFileHelper::Reply, Owned(helper), std::move(callback)));
}

bool FileProxy::Flush(StatusCallback callback) {
  DCHECK(file_.IsValid());
  GenericFileHelper* helper = new GenericFileHelper(this, std::move(file_));
  return task_runner_->PostTaskAndReply(
      FROM_HERE, BindOnce(&GenericFileHelper::Flush, Unretained(helper)),
      BindOnce(&GenericFileHelper::Reply, Owned(helper), std::move(callback)));
}

}  // namespace base
