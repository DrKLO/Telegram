# Pre-generated files

This directory contains a number of pre-generated build artifacts. To simplify
downstream builds, they are checked into the repository, rather than dynamically
generated as part of the build.

When developing on BoringSSL, if any inputs to these files are modified, callers
must run the following command to update the generated files:

    go run ./util/pregenerate

To check that files are up-to-date without updating files, run:

    go run ./util/pregenerate -check

This is run on CI to ensure the generated files remain up-to-date.

To speed up local iteration, the tool accepts additional arguments to filter the
files generated. For example, if editing `aesni-x86_64.pl`, this
command will only update files with "aesni-x86_64" as a substring.

    go run ./util/pregenerate aesni-x86_64

For convenience, all files in this directory, including this README, are managed
by the tool. This means the whole directory may be deleted and regenerated from
scratch at any time.
