//
//  Copyright 2019 The Abseil Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef ABSL_FLAGS_INTERNAL_USAGE_H_
#define ABSL_FLAGS_INTERNAL_USAGE_H_

#include <iosfwd>
#include <string>

#include "absl/base/config.h"
#include "absl/flags/declare.h"
#include "absl/flags/internal/commandlineflag.h"
#include "absl/strings/string_view.h"

// --------------------------------------------------------------------
// Usage reporting interfaces

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace flags_internal {

// The format to report the help messages in.
enum class HelpFormat {
  kHumanReadable,
};

// Outputs the help message describing specific flag.
void FlagHelp(std::ostream& out, const flags_internal::CommandLineFlag& flag,
              HelpFormat format = HelpFormat::kHumanReadable);

// Produces the help messages for all flags matching the filter. A flag matches
// the filter if it is defined in a file with a filename which includes
// filter string as a substring. You can use '/' and '.' to restrict the
// matching to a specific file names. For example:
//   FlagsHelp(out, "/path/to/file.");
// restricts help to only flags which resides in files named like:
//  .../path/to/file.<ext>
// for any extension 'ext'. If the filter is empty this function produces help
// messages for all flags.
void FlagsHelp(std::ostream& out, absl::string_view filter,
               HelpFormat format, absl::string_view program_usage_message);

// --------------------------------------------------------------------

// If any of the 'usage' related command line flags (listed on the bottom of
// this file) has been set this routine produces corresponding help message in
// the specified output stream and returns:
//  0 - if "version" or "only_check_flags" flags were set and handled.
//  1 - if some other 'usage' related flag was set and handled.
// -1 - if no usage flags were set on a commmand line.
// Non negative return values are expected to be used as an exit code for a
// binary.
int HandleUsageFlags(std::ostream& out,
                     absl::string_view program_usage_message);

}  // namespace flags_internal
ABSL_NAMESPACE_END
}  // namespace absl

ABSL_DECLARE_FLAG(bool, help);
ABSL_DECLARE_FLAG(bool, helpfull);
ABSL_DECLARE_FLAG(bool, helpshort);
ABSL_DECLARE_FLAG(bool, helppackage);
ABSL_DECLARE_FLAG(bool, version);
ABSL_DECLARE_FLAG(bool, only_check_args);
ABSL_DECLARE_FLAG(std::string, helpon);
ABSL_DECLARE_FLAG(std::string, helpmatch);

#endif  // ABSL_FLAGS_INTERNAL_USAGE_H_
