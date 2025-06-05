# Copyright 2016 The BoringSSL Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This BUILD file is used as part of the synthesized "-with-bazel" branches. We
# are in the process of transitioning to keeping all builds at tip-of-tree
# directly, see https://crbug.com/boringssl/542. This means, in the synthesized
# branch, "src" now reads as a separate Bazel package. Rather than suppress
# those build files, alias the targets.

alias(
    name = "crypto",
    actual = "//src:crypto",
    visibility = ["//visibility:public"],
)

alias(
    name = "ssl",
    actual = "//src:ssl",
    visibility = ["//visibility:public"],
)

alias(
    name = "bssl",
    actual = "//src:bssl",
    visibility = ["//visibility:public"],
)
