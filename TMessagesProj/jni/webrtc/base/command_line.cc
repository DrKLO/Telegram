// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/command_line.h"

#include <algorithm>
#include <ostream>

#include "base/containers/span.h"
#include "base/files/file_path.h"
#include "base/logging.h"
#include "base/stl_util.h"
#include "base/strings/string_split.h"
#include "base/strings/string_tokenizer.h"
#include "base/strings/string_util.h"
#include "base/strings/utf_string_conversions.h"
#include "build/build_config.h"

#if defined(OS_WIN)
#include <windows.h>
#include <shellapi.h>

#include "base/strings/string_util_win.h"
#endif

namespace base {

CommandLine* CommandLine::current_process_commandline_ = nullptr;

namespace {

constexpr CommandLine::CharType kSwitchTerminator[] = FILE_PATH_LITERAL("--");
constexpr CommandLine::CharType kSwitchValueSeparator[] =
    FILE_PATH_LITERAL("=");

// Since we use a lazy match, make sure that longer versions (like "--") are
// listed before shorter versions (like "-") of similar prefixes.
#if defined(OS_WIN)
// By putting slash last, we can control whether it is treaded as a switch
// value by changing the value of switch_prefix_count to be one less than
// the array size.
constexpr CommandLine::StringPieceType kSwitchPrefixes[] = {L"--", L"-", L"/"};
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
// Unixes don't use slash as a switch.
constexpr CommandLine::StringPieceType kSwitchPrefixes[] = {"--", "-"};
#endif
size_t switch_prefix_count = base::size(kSwitchPrefixes);

size_t GetSwitchPrefixLength(CommandLine::StringPieceType string) {
  for (size_t i = 0; i < switch_prefix_count; ++i) {
    CommandLine::StringType prefix(kSwitchPrefixes[i]);
    if (string.substr(0, prefix.length()) == prefix)
      return prefix.length();
  }
  return 0;
}

// Fills in |switch_string| and |switch_value| if |string| is a switch.
// This will preserve the input switch prefix in the output |switch_string|.
bool IsSwitch(const CommandLine::StringType& string,
              CommandLine::StringType* switch_string,
              CommandLine::StringType* switch_value) {
  switch_string->clear();
  switch_value->clear();
  size_t prefix_length = GetSwitchPrefixLength(string);
  if (prefix_length == 0 || prefix_length == string.length())
    return false;

  const size_t equals_position = string.find(kSwitchValueSeparator);
  *switch_string = string.substr(0, equals_position);
  if (equals_position != CommandLine::StringType::npos)
    *switch_value = string.substr(equals_position + 1);
  return true;
}

// Returns true iff |string| represents a switch with key
// |switch_key_without_prefix|, regardless of value.
bool IsSwitchWithKey(CommandLine::StringPieceType string,
                     CommandLine::StringPieceType switch_key_without_prefix) {
  size_t prefix_length = GetSwitchPrefixLength(string);
  if (prefix_length == 0 || prefix_length == string.length())
    return false;

  const size_t equals_position = string.find(kSwitchValueSeparator);
  return string.substr(prefix_length, equals_position - prefix_length) ==
         switch_key_without_prefix;
}

// Append switches and arguments, keeping switches before arguments.
void AppendSwitchesAndArguments(CommandLine* command_line,
                                const CommandLine::StringVector& argv) {
  bool parse_switches = true;
  for (size_t i = 1; i < argv.size(); ++i) {
    CommandLine::StringType arg = argv[i];
#if defined(OS_WIN)
    arg = CommandLine::StringType(TrimWhitespace(arg, TRIM_ALL));
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
    TrimWhitespaceASCII(arg, TRIM_ALL, &arg);
#endif

    CommandLine::StringType switch_string;
    CommandLine::StringType switch_value;
    parse_switches &= (arg != kSwitchTerminator);
    if (parse_switches && IsSwitch(arg, &switch_string, &switch_value)) {
#if defined(OS_WIN)
      command_line->AppendSwitchNative(WideToUTF8(switch_string), switch_value);
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
      command_line->AppendSwitchNative(switch_string, switch_value);
#else
#error Unsupported platform
#endif
    } else {
      command_line->AppendArgNative(arg);
    }
  }
}

#if defined(OS_WIN)
// Quote a string as necessary for CommandLineToArgvW compatiblity *on Windows*.
std::wstring QuoteForCommandLineToArgvW(const std::wstring& arg,
                                        bool quote_placeholders) {
  // We follow the quoting rules of CommandLineToArgvW.
  // http://msdn.microsoft.com/en-us/library/17w5ykft.aspx
  std::wstring quotable_chars(L" \\\"");
  // We may also be required to quote '%', which is commonly used in a command
  // line as a placeholder. (It may be substituted for a string with spaces.)
  if (quote_placeholders)
    quotable_chars.push_back('%');
  if (arg.find_first_of(quotable_chars) == std::wstring::npos) {
    // No quoting necessary.
    return arg;
  }

  std::wstring out;
  out.push_back('"');
  for (size_t i = 0; i < arg.size(); ++i) {
    if (arg[i] == '\\') {
      // Find the extent of this run of backslashes.
      size_t start = i, end = start + 1;
      for (; end < arg.size() && arg[end] == '\\'; ++end) {}
      size_t backslash_count = end - start;

      // Backslashes are escapes only if the run is followed by a double quote.
      // Since we also will end the string with a double quote, we escape for
      // either a double quote or the end of the string.
      if (end == arg.size() || arg[end] == '"') {
        // To quote, we need to output 2x as many backslashes.
        backslash_count *= 2;
      }
      for (size_t j = 0; j < backslash_count; ++j)
        out.push_back('\\');

      // Advance i to one before the end to balance i++ in loop.
      i = end - 1;
    } else if (arg[i] == '"') {
      out.push_back('\\');
      out.push_back('"');
    } else {
      out.push_back(arg[i]);
    }
  }
  out.push_back('"');

  return out;
}
#endif

}  // namespace

CommandLine::CommandLine(NoProgram no_program)
    : argv_(1),
      begin_args_(1) {
}

CommandLine::CommandLine(const FilePath& program)
    : argv_(1),
      begin_args_(1) {
  SetProgram(program);
}

CommandLine::CommandLine(int argc, const CommandLine::CharType* const* argv)
    : argv_(1),
      begin_args_(1) {
  InitFromArgv(argc, argv);
}

CommandLine::CommandLine(const StringVector& argv)
    : argv_(1),
      begin_args_(1) {
  InitFromArgv(argv);
}

CommandLine::CommandLine(const CommandLine& other) = default;

CommandLine& CommandLine::operator=(const CommandLine& other) = default;

CommandLine::~CommandLine() = default;

#if defined(OS_WIN)
// static
void CommandLine::set_slash_is_not_a_switch() {
  // The last switch prefix should be slash, so adjust the size to skip it.
  static_assert(base::make_span(kSwitchPrefixes).back() == L"/",
                "Error: Last switch prefix is not a slash.");
  switch_prefix_count = base::size(kSwitchPrefixes) - 1;
}

// static
void CommandLine::InitUsingArgvForTesting(int argc, const char* const* argv) {
  DCHECK(!current_process_commandline_);
  current_process_commandline_ = new CommandLine(NO_PROGRAM);
  // On Windows we need to convert the command line arguments to std::wstring.
  CommandLine::StringVector argv_vector;
  for (int i = 0; i < argc; ++i)
    argv_vector.push_back(UTF8ToWide(argv[i]));
  current_process_commandline_->InitFromArgv(argv_vector);
}
#endif

// static
bool CommandLine::Init(int argc, const char* const* argv) {
  if (current_process_commandline_) {
    // If this is intentional, Reset() must be called first. If we are using
    // the shared build mode, we have to share a single object across multiple
    // shared libraries.
    return false;
  }

  current_process_commandline_ = new CommandLine(NO_PROGRAM);
#if defined(OS_WIN)
  current_process_commandline_->ParseFromString(::GetCommandLineW());
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  current_process_commandline_->InitFromArgv(argc, argv);
#else
#error Unsupported platform
#endif

  return true;
}

// static
void CommandLine::Reset() {
  DCHECK(current_process_commandline_);
  delete current_process_commandline_;
  current_process_commandline_ = nullptr;
}

// static
CommandLine* CommandLine::ForCurrentProcess() {
  DCHECK(current_process_commandline_);
  return current_process_commandline_;
}

// static
bool CommandLine::InitializedForCurrentProcess() {
  return !!current_process_commandline_;
}

#if defined(OS_WIN)
// static
CommandLine CommandLine::FromString(StringPieceType command_line) {
  CommandLine cmd(NO_PROGRAM);
  cmd.ParseFromString(command_line);
  return cmd;
}
#endif

void CommandLine::InitFromArgv(int argc,
                               const CommandLine::CharType* const* argv) {
  StringVector new_argv;
  for (int i = 0; i < argc; ++i)
    new_argv.push_back(argv[i]);
  InitFromArgv(new_argv);
}

void CommandLine::InitFromArgv(const StringVector& argv) {
  argv_ = StringVector(1);
  switches_.clear();
  begin_args_ = 1;
  SetProgram(argv.empty() ? FilePath() : FilePath(argv[0]));
  AppendSwitchesAndArguments(this, argv);
}

FilePath CommandLine::GetProgram() const {
  return FilePath(argv_[0]);
}

void CommandLine::SetProgram(const FilePath& program) {
#if defined(OS_WIN)
  argv_[0] = StringType(TrimWhitespace(program.value(), TRIM_ALL));
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  TrimWhitespaceASCII(program.value(), TRIM_ALL, &argv_[0]);
#else
#error Unsupported platform
#endif
}

bool CommandLine::HasSwitch(const StringPiece& switch_string) const {
  DCHECK_EQ(ToLowerASCII(switch_string), switch_string);
  return Contains(switches_, switch_string);
}

bool CommandLine::HasSwitch(const char switch_constant[]) const {
  return HasSwitch(StringPiece(switch_constant));
}

std::string CommandLine::GetSwitchValueASCII(
    const StringPiece& switch_string) const {
  StringType value = GetSwitchValueNative(switch_string);
#if defined(OS_WIN)
  if (!IsStringASCII(base::AsStringPiece16(value))) {
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  if (!IsStringASCII(value)) {
#endif
    DLOG(WARNING) << "Value of switch (" << switch_string << ") must be ASCII.";
    return std::string();
  }
#if defined(OS_WIN)
  return WideToUTF8(value);
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  return value;
#endif
}

FilePath CommandLine::GetSwitchValuePath(
    const StringPiece& switch_string) const {
  return FilePath(GetSwitchValueNative(switch_string));
}

CommandLine::StringType CommandLine::GetSwitchValueNative(
    const StringPiece& switch_string) const {
  DCHECK_EQ(ToLowerASCII(switch_string), switch_string);
  auto result = switches_.find(switch_string);
  return result == switches_.end() ? StringType() : result->second;
}

void CommandLine::AppendSwitch(const std::string& switch_string) {
  AppendSwitchNative(switch_string, StringType());
}

void CommandLine::AppendSwitchPath(const std::string& switch_string,
                                   const FilePath& path) {
  AppendSwitchNative(switch_string, path.value());
}

void CommandLine::AppendSwitchNative(const std::string& switch_string,
                                     const CommandLine::StringType& value) {
#if defined(OS_WIN)
  const std::string switch_key = ToLowerASCII(switch_string);
  StringType combined_switch_string(UTF8ToWide(switch_key));
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  const std::string& switch_key = switch_string;
  StringType combined_switch_string(switch_key);
#endif
  size_t prefix_length = GetSwitchPrefixLength(combined_switch_string);
  auto insertion =
      switches_.insert(make_pair(switch_key.substr(prefix_length), value));
  if (!insertion.second)
    insertion.first->second = value;
  // Preserve existing switch prefixes in |argv_|; only append one if necessary.
  if (prefix_length == 0) {
    combined_switch_string.insert(0, kSwitchPrefixes[0].data(),
                                  kSwitchPrefixes[0].size());
  }
  if (!value.empty())
    combined_switch_string += kSwitchValueSeparator + value;
  // Append the switch and update the switches/arguments divider |begin_args_|.
  argv_.insert(argv_.begin() + begin_args_++, combined_switch_string);
}

void CommandLine::AppendSwitchASCII(const std::string& switch_string,
                                    const std::string& value_string) {
#if defined(OS_WIN)
  AppendSwitchNative(switch_string, UTF8ToWide(value_string));
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  AppendSwitchNative(switch_string, value_string);
#else
#error Unsupported platform
#endif
}

void CommandLine::RemoveSwitch(base::StringPiece switch_key_without_prefix) {
#if defined(OS_WIN)
  StringType switch_key_native = UTF8ToWide(switch_key_without_prefix);
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  StringType switch_key_native = switch_key_without_prefix.as_string();
#endif

  DCHECK_EQ(ToLowerASCII(switch_key_without_prefix), switch_key_without_prefix);
  DCHECK_EQ(0u, GetSwitchPrefixLength(switch_key_native));
  size_t erased_from_switches =
      switches_.erase(switch_key_without_prefix.as_string());
  DCHECK(erased_from_switches <= 1);
  if (!erased_from_switches)
    return;

  // Also erase from the switches section of |argv_| and update |begin_args_|
  // accordingly.
  // Switches in |argv_| have indices [1, begin_args_).
  auto argv_switches_begin = argv_.begin() + 1;
  auto argv_switches_end = argv_.begin() + begin_args_;
  DCHECK(argv_switches_begin <= argv_switches_end);
  DCHECK(argv_switches_end <= argv_.end());
  auto expell = std::remove_if(argv_switches_begin, argv_switches_end,
                               [&switch_key_native](const StringType& arg) {
                                 return IsSwitchWithKey(arg, switch_key_native);
                               });
  if (expell == argv_switches_end) {
    NOTREACHED();
    return;
  }
  begin_args_ -= argv_switches_end - expell;
  argv_.erase(expell, argv_switches_end);
}

void CommandLine::CopySwitchesFrom(const CommandLine& source,
                                   const char* const switches[],
                                   size_t count) {
  for (size_t i = 0; i < count; ++i) {
    if (source.HasSwitch(switches[i]))
      AppendSwitchNative(switches[i], source.GetSwitchValueNative(switches[i]));
  }
}

CommandLine::StringVector CommandLine::GetArgs() const {
  // Gather all arguments after the last switch (may include kSwitchTerminator).
  StringVector args(argv_.begin() + begin_args_, argv_.end());
  // Erase only the first kSwitchTerminator (maybe "--" is a legitimate page?)
  auto switch_terminator =
      std::find(args.begin(), args.end(), kSwitchTerminator);
  if (switch_terminator != args.end())
    args.erase(switch_terminator);
  return args;
}

void CommandLine::AppendArg(const std::string& value) {
#if defined(OS_WIN)
  DCHECK(IsStringUTF8(value));
  AppendArgNative(UTF8ToWide(value));
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  AppendArgNative(value);
#else
#error Unsupported platform
#endif
}

void CommandLine::AppendArgPath(const FilePath& path) {
  AppendArgNative(path.value());
}

void CommandLine::AppendArgNative(const CommandLine::StringType& value) {
  argv_.push_back(value);
}

void CommandLine::AppendArguments(const CommandLine& other,
                                  bool include_program) {
  if (include_program)
    SetProgram(other.GetProgram());
  AppendSwitchesAndArguments(this, other.argv());
}

void CommandLine::PrependWrapper(const CommandLine::StringType& wrapper) {
  if (wrapper.empty())
    return;
  // Split the wrapper command based on whitespace (with quoting).
  using CommandLineTokenizer =
      StringTokenizerT<StringType, StringType::const_iterator>;
  CommandLineTokenizer tokenizer(wrapper, FILE_PATH_LITERAL(" "));
  tokenizer.set_quote_chars(FILE_PATH_LITERAL("'\""));
  std::vector<StringType> wrapper_argv;
  while (tokenizer.GetNext())
    wrapper_argv.emplace_back(tokenizer.token());

  // Prepend the wrapper and update the switches/arguments |begin_args_|.
  argv_.insert(argv_.begin(), wrapper_argv.begin(), wrapper_argv.end());
  begin_args_ += wrapper_argv.size();
}

#if defined(OS_WIN)
void CommandLine::ParseFromString(StringPieceType command_line) {
  command_line = TrimWhitespace(command_line, TRIM_ALL);
  if (command_line.empty())
    return;

  int num_args = 0;
  wchar_t** args = NULL;
  // When calling CommandLineToArgvW, use the apiset if available.
  // Doing so will bypass loading shell32.dll on Win8+.
  HMODULE downlevel_shell32_dll =
      ::LoadLibraryEx(L"api-ms-win-downlevel-shell32-l1-1-0.dll", nullptr,
                      LOAD_LIBRARY_SEARCH_SYSTEM32);
  if (downlevel_shell32_dll) {
    auto command_line_to_argv_w_proc =
        reinterpret_cast<decltype(::CommandLineToArgvW)*>(
            ::GetProcAddress(downlevel_shell32_dll, "CommandLineToArgvW"));
    if (command_line_to_argv_w_proc)
      args = command_line_to_argv_w_proc(command_line.data(), &num_args);
  } else {
    // Since the apiset is not available, allow the delayload of shell32.dll
    // to take place.
    args = ::CommandLineToArgvW(command_line.data(), &num_args);
  }

  DPLOG_IF(FATAL, !args) << "CommandLineToArgvW failed on command line: "
                         << command_line;
  StringVector argv(args, args + num_args);
  InitFromArgv(argv);
  LocalFree(args);

  if (downlevel_shell32_dll)
    ::FreeLibrary(downlevel_shell32_dll);
}
#endif

CommandLine::StringType CommandLine::GetCommandLineStringInternal(
    bool quote_placeholders) const {
  StringType string(argv_[0]);
#if defined(OS_WIN)
  string = QuoteForCommandLineToArgvW(string, quote_placeholders);
#endif
  StringType params(GetArgumentsStringInternal(quote_placeholders));
  if (!params.empty()) {
    string.append(FILE_PATH_LITERAL(" "));
    string.append(params);
  }
  return string;
}

CommandLine::StringType CommandLine::GetArgumentsStringInternal(
    bool quote_placeholders) const {
  StringType params;
  // Append switches and arguments.
  bool parse_switches = true;
  for (size_t i = 1; i < argv_.size(); ++i) {
    StringType arg = argv_[i];
    StringType switch_string;
    StringType switch_value;
    parse_switches &= arg != kSwitchTerminator;
    if (i > 1)
      params.append(FILE_PATH_LITERAL(" "));
    if (parse_switches && IsSwitch(arg, &switch_string, &switch_value)) {
      params.append(switch_string);
      if (!switch_value.empty()) {
#if defined(OS_WIN)
        switch_value =
            QuoteForCommandLineToArgvW(switch_value, quote_placeholders);
#endif
        params.append(kSwitchValueSeparator + switch_value);
      }
    } else {
#if defined(OS_WIN)
      arg = QuoteForCommandLineToArgvW(arg, quote_placeholders);
#endif
      params.append(arg);
    }
  }
  return params;
}

}  // namespace base
