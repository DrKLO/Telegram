# Copyright (c) 2015, Google Inc.
#
# Permission to use, copy, modify, and/or distribute this software for any
# purpose with or without fee is hereby granted, provided that the above
# copyright notice and this permission notice appear in all copies.
#
# THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
# WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
# SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
# WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
# OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
# CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

"""Extracts archives."""


import hashlib
import optparse
import os
import os.path
import tarfile
import shutil
import sys
import zipfile


def CheckedJoin(output, path):
  """
  CheckedJoin returns os.path.join(output, path). It does sanity checks to
  ensure the resulting path is under output, but shouldn't be used on untrusted
  input.
  """
  path = os.path.normpath(path)
  if os.path.isabs(path) or path.startswith('.'):
    raise ValueError(path)
  return os.path.join(output, path)


class FileEntry(object):
  def __init__(self, path, mode, fileobj):
    self.path = path
    self.mode = mode
    self.fileobj = fileobj


class SymlinkEntry(object):
  def __init__(self, path, mode, target):
    self.path = path
    self.mode = mode
    self.target = target


def IterateZip(path):
  """
  IterateZip opens the zip file at path and returns a generator of entry objects
  for each file in it.
  """
  with zipfile.ZipFile(path, 'r') as zip_file:
    for info in zip_file.infolist():
      if info.filename.endswith('/'):
        continue
      yield FileEntry(info.filename, None, zip_file.open(info))


def IterateTar(path, compression):
  """
  IterateTar opens the tar.gz or tar.bz2 file at path and returns a generator of
  entry objects for each file in it.
  """
  with tarfile.open(path, 'r:' + compression) as tar_file:
    for info in tar_file:
      if info.isdir():
        pass
      elif info.issym():
        yield SymlinkEntry(info.name, None, info.linkname)
      elif info.isfile():
        yield FileEntry(info.name, info.mode, tar_file.extractfile(info))
      else:
        raise ValueError('Unknown entry type "%s"' % (info.name, ))


def main(args):
  parser = optparse.OptionParser(usage='Usage: %prog ARCHIVE OUTPUT')
  parser.add_option('--no-prefix', dest='no_prefix', action='store_true',
                    help='Do not remove a prefix from paths in the archive.')
  options, args = parser.parse_args(args)

  if len(args) != 2:
    parser.print_help()
    return 1

  archive, output = args

  if not os.path.exists(archive):
    # Skip archives that weren't downloaded.
    return 0

  with open(archive) as f:
    sha256 = hashlib.sha256()
    while True:
      chunk = f.read(1024 * 1024)
      if not chunk:
        break
      sha256.update(chunk)
    digest = sha256.hexdigest()

  stamp_path = os.path.join(output, ".boringssl_archive_digest")
  if os.path.exists(stamp_path):
    with open(stamp_path) as f:
      if f.read().strip() == digest:
        print "Already up-to-date."
        return 0

  if archive.endswith('.zip'):
    entries = IterateZip(archive)
  elif archive.endswith('.tar.gz'):
    entries = IterateTar(archive, 'gz')
  elif archive.endswith('.tar.bz2'):
    entries = IterateTar(archive, 'bz2')
  else:
    raise ValueError(archive)

  try:
    if os.path.exists(output):
      print "Removing %s" % (output, )
      shutil.rmtree(output)

    print "Extracting %s to %s" % (archive, output)
    prefix = None
    num_extracted = 0
    for entry in entries:
      # Even on Windows, zip files must always use forward slashes.
      if '\\' in entry.path or entry.path.startswith('/'):
        raise ValueError(entry.path)

      if not options.no_prefix:
        new_prefix, rest = entry.path.split('/', 1)

        # Ensure the archive is consistent.
        if prefix is None:
          prefix = new_prefix
        if prefix != new_prefix:
          raise ValueError((prefix, new_prefix))
      else:
        rest = entry.path

      # Extract the file into the output directory.
      fixed_path = CheckedJoin(output, rest)
      if not os.path.isdir(os.path.dirname(fixed_path)):
        os.makedirs(os.path.dirname(fixed_path))
      if isinstance(entry, FileEntry):
        with open(fixed_path, 'wb') as out:
          shutil.copyfileobj(entry.fileobj, out)
      elif isinstance(entry, SymlinkEntry):
        os.symlink(entry.target, fixed_path)
      else:
        raise TypeError('unknown entry type')

      # Fix up permissions if needbe.
      # TODO(davidben): To be extra tidy, this should only track the execute bit
      # as in git.
      if entry.mode is not None:
        os.chmod(fixed_path, entry.mode)

      # Print every 100 files, so bots do not time out on large archives.
      num_extracted += 1
      if num_extracted % 100 == 0:
        print "Extracted %d files..." % (num_extracted,)
  finally:
    entries.close()

  with open(stamp_path, 'w') as f:
    f.write(digest)

  print "Done. Extracted %d files." % (num_extracted,)
  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
