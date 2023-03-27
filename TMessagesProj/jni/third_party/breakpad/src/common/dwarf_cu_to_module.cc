// Copyright (c) 2010 Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

// Original author: Jim Blandy <jimb@mozilla.com> <jimb@red-bean.com>

// Implement the DwarfCUToModule class; see dwarf_cu_to_module.h.

// For <inttypes.h> PRI* macros, before anything else might #include it.
#ifndef __STDC_FORMAT_MACROS
#define __STDC_FORMAT_MACROS
#endif  /* __STDC_FORMAT_MACROS */

#include "common/dwarf_cu_to_module.h"

#include <assert.h>
#if !defined(__ANDROID__)
#include <cxxabi.h>
#endif
#include <inttypes.h>
#include <stdio.h>

#include <algorithm>
#include <utility>

#include "common/dwarf_line_to_module.h"
#include "common/unordered.h"

namespace google_breakpad {

using std::map;
using std::pair;
using std::sort;
using std::vector;

// Data provided by a DWARF specification DIE.
//
// In DWARF, the DIE for a definition may contain a DW_AT_specification
// attribute giving the offset of the corresponding declaration DIE, and
// the definition DIE may omit information given in the declaration. For
// example, it's common for a function's address range to appear only in
// its definition DIE, but its name to appear only in its declaration
// DIE.
//
// The dumper needs to be able to follow DW_AT_specification links to
// bring all this information together in a FUNC record. Conveniently,
// DIEs that are the target of such links have a DW_AT_declaration flag
// set, so we can identify them when we first see them, and record their
// contents for later reference.
//
// A Specification holds information gathered from a declaration DIE that
// we may need if we find a DW_AT_specification link pointing to it.
struct DwarfCUToModule::Specification {
  // The qualified name that can be found by demangling DW_AT_MIPS_linkage_name.
  string qualified_name;

  // The name of the enclosing scope, or the empty string if there is none.
  string enclosing_name;

  // The name for the specification DIE itself, without any enclosing
  // name components.
  string unqualified_name;
};

// An abstract origin -- base definition of an inline function.
struct AbstractOrigin {
  AbstractOrigin() : name() {}
  explicit AbstractOrigin(const string& name) : name(name) {}

  string name;
};

typedef map<uint64, AbstractOrigin> AbstractOriginByOffset;

// Data global to the DWARF-bearing file that is private to the
// DWARF-to-Module process.
struct DwarfCUToModule::FilePrivate {
  // A set of strings used in this CU. Before storing a string in one of
  // our data structures, insert it into this set, and then use the string
  // from the set.
  //
  // In some STL implementations, strings are reference-counted internally,
  // meaning that simply using strings from this set, even if passed by
  // value, assigned, or held directly in structures and containers
  // (map<string, ...>, for example), causes those strings to share a
  // single instance of each distinct piece of text. GNU's libstdc++ uses
  // reference counts, and I believe MSVC did as well, at some point.
  // However, C++ '11 implementations are moving away from reference
  // counting.
  //
  // In other implementations, string assignments copy the string's text,
  // so this set will actually hold yet another copy of the string (although
  // everything will still work). To improve memory consumption portably,
  // we will probably need to use pointers to strings held in this set.
  unordered_set<string> common_strings;

  // A map from offsets of DIEs within the .debug_info section to
  // Specifications describing those DIEs. Specification references can
  // cross compilation unit boundaries.
  SpecificationByOffset specifications;

  AbstractOriginByOffset origins;
};

DwarfCUToModule::FileContext::FileContext(const string &filename,
                                          Module *module,
                                          bool handle_inter_cu_refs)
    : filename_(filename),
      module_(module),
      handle_inter_cu_refs_(handle_inter_cu_refs),
      file_private_(new FilePrivate()) {
}

DwarfCUToModule::FileContext::~FileContext() {
}

void DwarfCUToModule::FileContext::AddSectionToSectionMap(
    const string& name, const char* contents, uint64 length) {
  section_map_[name] = std::make_pair(contents, length);
}

void DwarfCUToModule::FileContext::ClearSectionMapForTest() {
  section_map_.clear();
}

const dwarf2reader::SectionMap&
DwarfCUToModule::FileContext::section_map() const {
  return section_map_;
}

void DwarfCUToModule::FileContext::ClearSpecifications() {
  if (!handle_inter_cu_refs_)
    file_private_->specifications.clear();
}

bool DwarfCUToModule::FileContext::IsUnhandledInterCUReference(
    uint64 offset, uint64 compilation_unit_start) const {
  if (handle_inter_cu_refs_)
    return false;
  return offset < compilation_unit_start;
}

// Information global to the particular compilation unit we're
// parsing. This is for data shared across the CU's entire DIE tree,
// and parameters from the code invoking the CU parser.
struct DwarfCUToModule::CUContext {
  CUContext(FileContext *file_context_arg, WarningReporter *reporter_arg)
      : file_context(file_context_arg),
        reporter(reporter_arg),
        language(Language::CPlusPlus) {}

  ~CUContext() {
    for (vector<Module::Function *>::iterator it = functions.begin();
         it != functions.end(); ++it) {
      delete *it;
    }
  };

  // The DWARF-bearing file into which this CU was incorporated.
  FileContext *file_context;

  // For printing error messages.
  WarningReporter *reporter;

  // The source language of this compilation unit.
  const Language *language;

  // The functions defined in this compilation unit. We accumulate
  // them here during parsing. Then, in DwarfCUToModule::Finish, we
  // assign them lines and add them to file_context->module.
  //
  // Destroying this destroys all the functions this vector points to.
  vector<Module::Function *> functions;
};

// Information about the context of a particular DIE. This is for
// information that changes as we descend the tree towards the leaves:
// the containing classes/namespaces, etc.
struct DwarfCUToModule::DIEContext {
  // The fully-qualified name of the context. For example, for a
  // tree like:
  //
  // DW_TAG_namespace Foo
  //   DW_TAG_class Bar
  //     DW_TAG_subprogram Baz
  //
  // in a C++ compilation unit, the DIEContext's name for the
  // DW_TAG_subprogram DIE would be "Foo::Bar". The DIEContext's
  // name for the DW_TAG_namespace DIE would be "".
  string name;
};

// An abstract base class for all the dumper's DIE handlers.
class DwarfCUToModule::GenericDIEHandler: public dwarf2reader::DIEHandler {
 public:
  // Create a handler for the DIE at OFFSET whose compilation unit is
  // described by CU_CONTEXT, and whose immediate context is described
  // by PARENT_CONTEXT.
  GenericDIEHandler(CUContext *cu_context, DIEContext *parent_context,
                    uint64 offset)
      : cu_context_(cu_context),
        parent_context_(parent_context),
        offset_(offset),
        declaration_(false),
        specification_(NULL) { }

  // Derived classes' ProcessAttributeUnsigned can defer to this to
  // handle DW_AT_declaration, or simply not override it.
  void ProcessAttributeUnsigned(enum DwarfAttribute attr,
                                enum DwarfForm form,
                                uint64 data);

  // Derived classes' ProcessAttributeReference can defer to this to
  // handle DW_AT_specification, or simply not override it.
  void ProcessAttributeReference(enum DwarfAttribute attr,
                                 enum DwarfForm form,
                                 uint64 data);

  // Derived classes' ProcessAttributeReference can defer to this to
  // handle DW_AT_specification, or simply not override it.
  void ProcessAttributeString(enum DwarfAttribute attr,
                              enum DwarfForm form,
                              const string &data);

 protected:
  // Compute and return the fully-qualified name of the DIE. If this
  // DIE is a declaration DIE, to be cited by other DIEs'
  // DW_AT_specification attributes, record its enclosing name and
  // unqualified name in the specification table.
  //
  // Use this from EndAttributes member functions, not ProcessAttribute*
  // functions; only the former can be sure that all the DIE's attributes
  // have been seen.
  string ComputeQualifiedName();

  CUContext *cu_context_;
  DIEContext *parent_context_;
  uint64 offset_;

  // Place the name in the global set of strings. Even though this looks
  // like a copy, all the major std::string implementations use reference
  // counting internally, so the effect is to have all the data structures
  // share copies of strings whenever possible.
  // FIXME: Should this return something like a string_ref to avoid the
  // assumption about how strings are implemented?
  string AddStringToPool(const string &str);

  // If this DIE has a DW_AT_declaration attribute, this is its value.
  // It is false on DIEs with no DW_AT_declaration attribute.
  bool declaration_;

  // If this DIE has a DW_AT_specification attribute, this is the
  // Specification structure for the DIE the attribute refers to.
  // Otherwise, this is NULL.
  Specification *specification_;

  // The value of the DW_AT_name attribute, or the empty string if the
  // DIE has no such attribute.
  string name_attribute_;

  // The demangled value of the DW_AT_MIPS_linkage_name attribute, or the empty
  // string if the DIE has no such attribute or its content could not be
  // demangled.
  string demangled_name_;
};

void DwarfCUToModule::GenericDIEHandler::ProcessAttributeUnsigned(
    enum DwarfAttribute attr,
    enum DwarfForm form,
    uint64 data) {
  switch (attr) {
    case dwarf2reader::DW_AT_declaration: declaration_ = (data != 0); break;
    default: break;
  }
}

void DwarfCUToModule::GenericDIEHandler::ProcessAttributeReference(
    enum DwarfAttribute attr,
    enum DwarfForm form,
    uint64 data) {
  switch (attr) {
    case dwarf2reader::DW_AT_specification: {
      FileContext *file_context = cu_context_->file_context;
      if (file_context->IsUnhandledInterCUReference(
              data, cu_context_->reporter->cu_offset())) {
        cu_context_->reporter->UnhandledInterCUReference(offset_, data);
        break;
      }
      // Find the Specification to which this attribute refers, and
      // set specification_ appropriately. We could do more processing
      // here, but it's better to leave the real work to our
      // EndAttribute member function, at which point we know we have
      // seen all the DIE's attributes.
      SpecificationByOffset *specifications =
          &file_context->file_private_->specifications;
      SpecificationByOffset::iterator spec = specifications->find(data);
      if (spec != specifications->end()) {
        specification_ = &spec->second;
      } else {
        // Technically, there's no reason a DW_AT_specification
        // couldn't be a forward reference, but supporting that would
        // be a lot of work (changing to a two-pass structure), and I
        // don't think any producers we care about ever emit such
        // things.
        cu_context_->reporter->UnknownSpecification(offset_, data);
      }
      break;
    }
    default: break;
  }
}

string DwarfCUToModule::GenericDIEHandler::AddStringToPool(const string &str) {
  pair<unordered_set<string>::iterator, bool> result =
    cu_context_->file_context->file_private_->common_strings.insert(str);
  return *result.first;
}

void DwarfCUToModule::GenericDIEHandler::ProcessAttributeString(
    enum DwarfAttribute attr,
    enum DwarfForm form,
    const string &data) {
  switch (attr) {
    case dwarf2reader::DW_AT_name:
      name_attribute_ = AddStringToPool(data);
      break;
    case dwarf2reader::DW_AT_MIPS_linkage_name: {
      char* demangled = NULL;
      int status = -1;
#if !defined(__ANDROID__)  // Android NDK doesn't provide abi::__cxa_demangle.
      demangled = abi::__cxa_demangle(data.c_str(), NULL, NULL, &status);
#endif
      if (status != 0) {
        cu_context_->reporter->DemangleError(data, status);
        demangled_name_ = "";
        break;
      }
      if (demangled) {
        demangled_name_ = AddStringToPool(demangled);
        free(reinterpret_cast<void*>(demangled));
      }
      break;
    }
    default: break;
  }
}

string DwarfCUToModule::GenericDIEHandler::ComputeQualifiedName() {
  // Use the demangled name, if one is available. Demangled names are
  // preferable to those inferred from the DWARF structure because they
  // include argument types.
  const string *qualified_name = NULL;
  if (!demangled_name_.empty()) {
    // Found it is this DIE.
    qualified_name = &demangled_name_;
  } else if (specification_ && !specification_->qualified_name.empty()) {
    // Found it on the specification.
    qualified_name = &specification_->qualified_name;
  }

  const string *unqualified_name;
  const string *enclosing_name;
  if (!qualified_name) {
    // Find our unqualified name. If the DIE has its own DW_AT_name
    // attribute, then use that; otherwise, check our specification.
    if (name_attribute_.empty() && specification_)
      unqualified_name = &specification_->unqualified_name;
    else
      unqualified_name = &name_attribute_;

    // Find the name of our enclosing context. If we have a
    // specification, it's the specification's enclosing context that
    // counts; otherwise, use this DIE's context.
    if (specification_)
      enclosing_name = &specification_->enclosing_name;
    else
      enclosing_name = &parent_context_->name;
  }

  // Prepare the return value before upcoming mutations possibly invalidate the
  // existing pointers.
  string return_value;
  if (qualified_name) {
    return_value = *qualified_name;
  } else {
    // Combine the enclosing name and unqualified name to produce our
    // own fully-qualified name.
    return_value = cu_context_->language->MakeQualifiedName(*enclosing_name,
                                                            *unqualified_name);
  }

  // If this DIE was marked as a declaration, record its names in the
  // specification table.
  if (declaration_) {
    Specification spec;
    if (qualified_name) {
      spec.qualified_name = *qualified_name;
    } else {
      spec.enclosing_name = *enclosing_name;
      spec.unqualified_name = *unqualified_name;
    }
    cu_context_->file_context->file_private_->specifications[offset_] = spec;
  }

  return return_value;
}

// A handler class for DW_TAG_subprogram DIEs.
class DwarfCUToModule::FuncHandler: public GenericDIEHandler {
 public:
  FuncHandler(CUContext *cu_context, DIEContext *parent_context,
              uint64 offset)
      : GenericDIEHandler(cu_context, parent_context, offset),
        low_pc_(0), high_pc_(0), high_pc_form_(dwarf2reader::DW_FORM_addr),
        abstract_origin_(NULL), inline_(false) { }
  void ProcessAttributeUnsigned(enum DwarfAttribute attr,
                                enum DwarfForm form,
                                uint64 data);
  void ProcessAttributeSigned(enum DwarfAttribute attr,
                              enum DwarfForm form,
                              int64 data);
  void ProcessAttributeReference(enum DwarfAttribute attr,
                                 enum DwarfForm form,
                                 uint64 data);

  bool EndAttributes();
  void Finish();

 private:
  // The fully-qualified name, as derived from name_attribute_,
  // specification_, parent_context_.  Computed in EndAttributes.
  string name_;
  uint64 low_pc_, high_pc_; // DW_AT_low_pc, DW_AT_high_pc
  DwarfForm high_pc_form_; // DW_AT_high_pc can be length or address.
  const AbstractOrigin* abstract_origin_;
  bool inline_;
};

void DwarfCUToModule::FuncHandler::ProcessAttributeUnsigned(
    enum DwarfAttribute attr,
    enum DwarfForm form,
    uint64 data) {
  switch (attr) {
    // If this attribute is present at all --- even if its value is
    // DW_INL_not_inlined --- then GCC may cite it as someone else's
    // DW_AT_abstract_origin attribute.
    case dwarf2reader::DW_AT_inline:      inline_  = true; break;

    case dwarf2reader::DW_AT_low_pc:      low_pc_  = data; break;
    case dwarf2reader::DW_AT_high_pc:
      high_pc_form_ = form;
      high_pc_ = data;
      break;

    default:
      GenericDIEHandler::ProcessAttributeUnsigned(attr, form, data);
      break;
  }
}

void DwarfCUToModule::FuncHandler::ProcessAttributeSigned(
    enum DwarfAttribute attr,
    enum DwarfForm form,
    int64 data) {
  switch (attr) {
    // If this attribute is present at all --- even if its value is
    // DW_INL_not_inlined --- then GCC may cite it as someone else's
    // DW_AT_abstract_origin attribute.
    case dwarf2reader::DW_AT_inline:      inline_  = true; break;

    default:
      break;
  }
}

void DwarfCUToModule::FuncHandler::ProcessAttributeReference(
    enum DwarfAttribute attr,
    enum DwarfForm form,
    uint64 data) {
  switch (attr) {
    case dwarf2reader::DW_AT_abstract_origin: {
      const AbstractOriginByOffset& origins =
          cu_context_->file_context->file_private_->origins;
      AbstractOriginByOffset::const_iterator origin = origins.find(data);
      if (origin != origins.end()) {
        abstract_origin_ = &(origin->second);
      } else {
        cu_context_->reporter->UnknownAbstractOrigin(offset_, data);
      }
      break;
    }
    default:
      GenericDIEHandler::ProcessAttributeReference(attr, form, data);
      break;
  }
}

bool DwarfCUToModule::FuncHandler::EndAttributes() {
  // Compute our name, and record a specification, if appropriate.
  name_ = ComputeQualifiedName();
  if (name_.empty() && abstract_origin_) {
    name_ = abstract_origin_->name;
  }
  return true;
}

void DwarfCUToModule::FuncHandler::Finish() {
  // Make high_pc_ an address, if it isn't already.
  if (high_pc_form_ != dwarf2reader::DW_FORM_addr) {
    high_pc_ += low_pc_;
  }

  // Did we collect the information we need?  Not all DWARF function
  // entries have low and high addresses (for example, inlined
  // functions that were never used), but all the ones we're
  // interested in cover a non-empty range of bytes.
  if (low_pc_ < high_pc_) {
    // Malformed DWARF may omit the name, but all Module::Functions must
    // have names.
    string name;
    if (!name_.empty()) {
      name = name_;
    } else {
      cu_context_->reporter->UnnamedFunction(offset_);
      name = "<name omitted>";
    }

    // Create a Module::Function based on the data we've gathered, and
    // add it to the functions_ list.
    scoped_ptr<Module::Function> func(new Module::Function(name, low_pc_));
    func->size = high_pc_ - low_pc_;
    func->parameter_size = 0;
    if (func->address) {
       // If the function address is zero this is a sign that this function
       // description is just empty debug data and should just be discarded.
       cu_context_->functions.push_back(func.release());
     }
  } else if (inline_) {
    AbstractOrigin origin(name_);
    cu_context_->file_context->file_private_->origins[offset_] = origin;
  }
}

// A handler for DIEs that contain functions and contribute a
// component to their names: namespaces, classes, etc.
class DwarfCUToModule::NamedScopeHandler: public GenericDIEHandler {
 public:
  NamedScopeHandler(CUContext *cu_context, DIEContext *parent_context,
                    uint64 offset)
      : GenericDIEHandler(cu_context, parent_context, offset) { }
  bool EndAttributes();
  DIEHandler *FindChildHandler(uint64 offset, enum DwarfTag tag);

 private:
  DIEContext child_context_; // A context for our children.
};

bool DwarfCUToModule::NamedScopeHandler::EndAttributes() {
  child_context_.name = ComputeQualifiedName();
  return true;
}

dwarf2reader::DIEHandler *DwarfCUToModule::NamedScopeHandler::FindChildHandler(
    uint64 offset,
    enum DwarfTag tag) {
  switch (tag) {
    case dwarf2reader::DW_TAG_subprogram:
      return new FuncHandler(cu_context_, &child_context_, offset);
    case dwarf2reader::DW_TAG_namespace:
    case dwarf2reader::DW_TAG_class_type:
    case dwarf2reader::DW_TAG_structure_type:
    case dwarf2reader::DW_TAG_union_type:
      return new NamedScopeHandler(cu_context_, &child_context_, offset);
    default:
      return NULL;
  }
}

void DwarfCUToModule::WarningReporter::CUHeading() {
  if (printed_cu_header_)
    return;
  fprintf(stderr, "%s: in compilation unit '%s' (offset 0x%llx):\n",
          filename_.c_str(), cu_name_.c_str(), cu_offset_);
  printed_cu_header_ = true;
}

void DwarfCUToModule::WarningReporter::UnknownSpecification(uint64 offset,
                                                            uint64 target) {
  CUHeading();
  fprintf(stderr, "%s: the DIE at offset 0x%llx has a DW_AT_specification"
          " attribute referring to the die at offset 0x%llx, which either"
          " was not marked as a declaration, or comes later in the file\n",
          filename_.c_str(), offset, target);
}

void DwarfCUToModule::WarningReporter::UnknownAbstractOrigin(uint64 offset,
                                                             uint64 target) {
  CUHeading();
  fprintf(stderr, "%s: the DIE at offset 0x%llx has a DW_AT_abstract_origin"
          " attribute referring to the die at offset 0x%llx, which either"
          " was not marked as an inline, or comes later in the file\n",
          filename_.c_str(), offset, target);
}

void DwarfCUToModule::WarningReporter::MissingSection(const string &name) {
  CUHeading();
  fprintf(stderr, "%s: warning: couldn't find DWARF '%s' section\n",
          filename_.c_str(), name.c_str());
}

void DwarfCUToModule::WarningReporter::BadLineInfoOffset(uint64 offset) {
  CUHeading();
  fprintf(stderr, "%s: warning: line number data offset beyond end"
          " of '.debug_line' section\n",
          filename_.c_str());
}

void DwarfCUToModule::WarningReporter::UncoveredHeading() {
  if (printed_unpaired_header_)
    return;
  CUHeading();
  fprintf(stderr, "%s: warning: skipping unpaired lines/functions:\n",
          filename_.c_str());
  printed_unpaired_header_ = true;
}

void DwarfCUToModule::WarningReporter::UncoveredFunction(
    const Module::Function &function) {
  if (!uncovered_warnings_enabled_)
    return;
  UncoveredHeading();
  fprintf(stderr, "    function%s: %s\n",
          function.size == 0 ? " (zero-length)" : "",
          function.name.c_str());
}

void DwarfCUToModule::WarningReporter::UncoveredLine(const Module::Line &line) {
  if (!uncovered_warnings_enabled_)
    return;
  UncoveredHeading();
  fprintf(stderr, "    line%s: %s:%d at 0x%" PRIx64 "\n",
          (line.size == 0 ? " (zero-length)" : ""),
          line.file->name.c_str(), line.number, line.address);
}

void DwarfCUToModule::WarningReporter::UnnamedFunction(uint64 offset) {
  CUHeading();
  fprintf(stderr, "%s: warning: function at offset 0x%llx has no name\n",
          filename_.c_str(), offset);
}

void DwarfCUToModule::WarningReporter::DemangleError(
    const string &input, int error) {
  CUHeading();
  fprintf(stderr, "%s: warning: failed to demangle %s with error %d\n",
          filename_.c_str(), input.c_str(), error);
}

void DwarfCUToModule::WarningReporter::UnhandledInterCUReference(
    uint64 offset, uint64 target) {
  CUHeading();
  fprintf(stderr, "%s: warning: the DIE at offset 0x%llx has a "
                  "DW_FORM_ref_addr attribute with an inter-CU reference to "
                  "0x%llx, but inter-CU reference handling is turned off.\n",
                  filename_.c_str(), offset, target);
}

DwarfCUToModule::DwarfCUToModule(FileContext *file_context,
                                 LineToModuleHandler *line_reader,
                                 WarningReporter *reporter)
    : line_reader_(line_reader),
      cu_context_(new CUContext(file_context, reporter)),
      child_context_(new DIEContext()),
      has_source_line_info_(false) {
}

DwarfCUToModule::~DwarfCUToModule() {
}

void DwarfCUToModule::ProcessAttributeSigned(enum DwarfAttribute attr,
                                             enum DwarfForm form,
                                             int64 data) {
  switch (attr) {
    case dwarf2reader::DW_AT_language: // source language of this CU
      SetLanguage(static_cast<DwarfLanguage>(data));
      break;
    default:
      break;
  }
}

void DwarfCUToModule::ProcessAttributeUnsigned(enum DwarfAttribute attr,
                                               enum DwarfForm form,
                                               uint64 data) {
  switch (attr) {
    case dwarf2reader::DW_AT_stmt_list: // Line number information.
      has_source_line_info_ = true;
      source_line_offset_ = data;
      break;
    case dwarf2reader::DW_AT_language: // source language of this CU
      SetLanguage(static_cast<DwarfLanguage>(data));
      break;
    default:
      break;
  }
}

void DwarfCUToModule::ProcessAttributeString(enum DwarfAttribute attr,
                                             enum DwarfForm form,
                                             const string &data) {
  switch (attr) {
    case dwarf2reader::DW_AT_name:
      cu_context_->reporter->SetCUName(data);
      break;
    case dwarf2reader::DW_AT_comp_dir:
      line_reader_->StartCompilationUnit(data);
      break;
    default:
      break;
  }
}

bool DwarfCUToModule::EndAttributes() {
  return true;
}

dwarf2reader::DIEHandler *DwarfCUToModule::FindChildHandler(
    uint64 offset,
    enum DwarfTag tag) {
  switch (tag) {
    case dwarf2reader::DW_TAG_subprogram:
      return new FuncHandler(cu_context_.get(), child_context_.get(), offset);
    case dwarf2reader::DW_TAG_namespace:
    case dwarf2reader::DW_TAG_class_type:
    case dwarf2reader::DW_TAG_structure_type:
    case dwarf2reader::DW_TAG_union_type:
      return new NamedScopeHandler(cu_context_.get(), child_context_.get(),
                                   offset);
    default:
      return NULL;
  }
}

void DwarfCUToModule::SetLanguage(DwarfLanguage language) {
  switch (language) {
    case dwarf2reader::DW_LANG_Java:
      cu_context_->language = Language::Java;
      break;

    // DWARF has no generic language code for assembly language; this is
    // what the GNU toolchain uses.
    case dwarf2reader::DW_LANG_Mips_Assembler:
      cu_context_->language = Language::Assembler;
      break;

    // C++ covers so many cases that it probably has some way to cope
    // with whatever the other languages throw at us. So make it the
    // default.
    //
    // Objective C and Objective C++ seem to create entries for
    // methods whose DW_AT_name values are already fully-qualified:
    // "-[Classname method:]".  These appear at the top level.
    //
    // DWARF data for C should never include namespaces or functions
    // nested in struct types, but if it ever does, then C++'s
    // notation is probably not a bad choice for that.
    default:
    case dwarf2reader::DW_LANG_ObjC:
    case dwarf2reader::DW_LANG_ObjC_plus_plus:
    case dwarf2reader::DW_LANG_C:
    case dwarf2reader::DW_LANG_C89:
    case dwarf2reader::DW_LANG_C99:
    case dwarf2reader::DW_LANG_C_plus_plus:
      cu_context_->language = Language::CPlusPlus;
      break;
  }
}

void DwarfCUToModule::ReadSourceLines(uint64 offset) {
  const dwarf2reader::SectionMap &section_map
      = cu_context_->file_context->section_map();
  dwarf2reader::SectionMap::const_iterator map_entry
      = section_map.find(".debug_line");
  // Mac OS X puts DWARF data in sections whose names begin with "__"
  // instead of ".".
  if (map_entry == section_map.end())
    map_entry = section_map.find("__debug_line");
  if (map_entry == section_map.end()) {
    cu_context_->reporter->MissingSection(".debug_line");
    return;
  }
  const char *section_start = map_entry->second.first;
  uint64 section_length = map_entry->second.second;
  if (offset >= section_length) {
    cu_context_->reporter->BadLineInfoOffset(offset);
    return;
  }
  line_reader_->ReadProgram(section_start + offset, section_length - offset,
                            cu_context_->file_context->module_, &lines_);
}

namespace {
// Return true if ADDRESS falls within the range of ITEM.
template <class T>
inline bool within(const T &item, Module::Address address) {
  // Because Module::Address is unsigned, and unsigned arithmetic
  // wraps around, this will be false if ADDRESS falls before the
  // start of ITEM, or if it falls after ITEM's end.
  return address - item.address < item.size;
}
}

void DwarfCUToModule::AssignLinesToFunctions() {
  vector<Module::Function *> *functions = &cu_context_->functions;
  WarningReporter *reporter = cu_context_->reporter;

  // This would be simpler if we assumed that source line entries
  // don't cross function boundaries.  However, there's no real reason
  // to assume that (say) a series of function definitions on the same
  // line wouldn't get coalesced into one line number entry.  The
  // DWARF spec certainly makes no such promises.
  //
  // So treat the functions and lines as peers, and take the trouble
  // to compute their ranges' intersections precisely.  In any case,
  // the hair here is a constant factor for performance; the
  // complexity from here on out is linear.

  // Put both our functions and lines in order by address.
  std::sort(functions->begin(), functions->end(),
            Module::Function::CompareByAddress);
  std::sort(lines_.begin(), lines_.end(), Module::Line::CompareByAddress);

  // The last line that we used any piece of.  We use this only for
  // generating warnings.
  const Module::Line *last_line_used = NULL;

  // The last function and line we warned about --- so we can avoid
  // doing so more than once.
  const Module::Function *last_function_cited = NULL;
  const Module::Line *last_line_cited = NULL;

  // Make a single pass through both vectors from lower to higher
  // addresses, populating each Function's lines vector with lines
  // from our lines_ vector that fall within the function's address
  // range.
  vector<Module::Function *>::iterator func_it = functions->begin();
  vector<Module::Line>::const_iterator line_it = lines_.begin();

  Module::Address current;

  // Pointers to the referents of func_it and line_it, or NULL if the
  // iterator is at the end of the sequence.
  Module::Function *func;
  const Module::Line *line;

  // Start current at the beginning of the first line or function,
  // whichever is earlier.
  if (func_it != functions->end() && line_it != lines_.end()) {
    func = *func_it;
    line = &*line_it;
    current = std::min(func->address, line->address);
  } else if (line_it != lines_.end()) {
    func = NULL;
    line = &*line_it;
    current = line->address;
  } else if (func_it != functions->end()) {
    func = *func_it;
    line = NULL;
    current = (*func_it)->address;
  } else {
    return;
  }

  while (func || line) {
    // This loop has two invariants that hold at the top.
    //
    // First, at least one of the iterators is not at the end of its
    // sequence, and those that are not refer to the earliest
    // function or line that contains or starts after CURRENT.
    //
    // Note that every byte is in one of four states: it is covered
    // or not covered by a function, and, independently, it is
    // covered or not covered by a line.
    //
    // The second invariant is that CURRENT refers to a byte whose
    // state is different from its predecessor, or it refers to the
    // first byte in the address space. In other words, CURRENT is
    // always the address of a transition.
    //
    // Note that, although each iteration advances CURRENT from one
    // transition address to the next in each iteration, it might
    // not advance the iterators. Suppose we have a function that
    // starts with a line, has a gap, and then a second line, and
    // suppose that we enter an iteration with CURRENT at the end of
    // the first line. The next transition address is the start of
    // the second line, after the gap, so the iteration should
    // advance CURRENT to that point. At the head of that iteration,
    // the invariants require that the line iterator be pointing at
    // the second line. But this is also true at the head of the
    // next. And clearly, the iteration must not change the function
    // iterator. So neither iterator moves.

    // Assert the first invariant (see above).
    assert(!func || current < func->address || within(*func, current));
    assert(!line || current < line->address || within(*line, current));

    // The next transition after CURRENT.
    Module::Address next_transition;

    // Figure out which state we're in, add lines or warn, and compute
    // the next transition address.
    if (func && current >= func->address) {
      if (line && current >= line->address) {
        // Covered by both a line and a function.
        Module::Address func_left = func->size - (current - func->address);
        Module::Address line_left = line->size - (current - line->address);
        // This may overflow, but things work out.
        next_transition = current + std::min(func_left, line_left);
        Module::Line l = *line;
        l.address = current;
        l.size = next_transition - current;
        func->lines.push_back(l);
        last_line_used = line;
      } else {
        // Covered by a function, but no line.
        if (func != last_function_cited) {
          reporter->UncoveredFunction(*func);
          last_function_cited = func;
        }
        if (line && within(*func, line->address))
          next_transition = line->address;
        else
          // If this overflows, we'll catch it below.
          next_transition = func->address + func->size;
      }
    } else {
      if (line && current >= line->address) {
        // Covered by a line, but no function.
        //
        // If GCC emits padding after one function to align the start
        // of the next, then it will attribute the padding
        // instructions to the last source line of function (to reduce
        // the size of the line number info), but omit it from the
        // DW_AT_{low,high}_pc range given in .debug_info (since it
        // costs nothing to be precise there). If we did use at least
        // some of the line we're about to skip, and it ends at the
        // start of the next function, then assume this is what
        // happened, and don't warn.
        if (line != last_line_cited
            && !(func
                 && line == last_line_used
                 && func->address - line->address == line->size)) {
          reporter->UncoveredLine(*line);
          last_line_cited = line;
        }
        if (func && within(*line, func->address))
          next_transition = func->address;
        else
          // If this overflows, we'll catch it below.
          next_transition = line->address + line->size;
      } else {
        // Covered by neither a function nor a line. By the invariant,
        // both func and line begin after CURRENT. The next transition
        // is the start of the next function or next line, whichever
        // is earliest.
        assert(func || line);
        if (func && line)
          next_transition = std::min(func->address, line->address);
        else if (func)
          next_transition = func->address;
        else
          next_transition = line->address;
      }
    }

    // If a function or line abuts the end of the address space, then
    // next_transition may end up being zero, in which case we've completed
    // our pass. Handle that here, instead of trying to deal with it in
    // each place we compute next_transition.
    if (!next_transition)
      break;

    // Advance iterators as needed. If lines overlap or functions overlap,
    // then we could go around more than once. We don't worry too much
    // about what result we produce in that case, just as long as we don't
    // hang or crash.
    while (func_it != functions->end()
           && next_transition >= (*func_it)->address
           && !within(**func_it, next_transition))
      func_it++;
    func = (func_it != functions->end()) ? *func_it : NULL;
    while (line_it != lines_.end()
           && next_transition >= line_it->address
           && !within(*line_it, next_transition))
      line_it++;
    line = (line_it != lines_.end()) ? &*line_it : NULL;

    // We must make progress.
    assert(next_transition > current);
    current = next_transition;
  }
}

void DwarfCUToModule::Finish() {
  // Assembly language files have no function data, and that gives us
  // no place to store our line numbers (even though the GNU toolchain
  // will happily produce source line info for assembly language
  // files).  To avoid spurious warnings about lines we can't assign
  // to functions, skip CUs in languages that lack functions.
  if (!cu_context_->language->HasFunctions())
    return;

  // Read source line info, if we have any.
  if (has_source_line_info_)
    ReadSourceLines(source_line_offset_);

  vector<Module::Function *> *functions = &cu_context_->functions;

  // Dole out lines to the appropriate functions.
  AssignLinesToFunctions();

  // Add our functions, which now have source lines assigned to them,
  // to module_.
  cu_context_->file_context->module_->AddFunctions(functions->begin(),
                                                   functions->end());

  // Ownership of the function objects has shifted from cu_context to
  // the Module.
  functions->clear();

  cu_context_->file_context->ClearSpecifications();
}

bool DwarfCUToModule::StartCompilationUnit(uint64 offset,
                                           uint8 address_size,
                                           uint8 offset_size,
                                           uint64 cu_length,
                                           uint8 dwarf_version) {
  return dwarf_version >= 2;
}

bool DwarfCUToModule::StartRootDIE(uint64 offset, enum DwarfTag tag) {
  // We don't deal with partial compilation units (the only other tag
  // likely to be used for root DIE).
  return tag == dwarf2reader::DW_TAG_compile_unit;
}

} // namespace google_breakpad
