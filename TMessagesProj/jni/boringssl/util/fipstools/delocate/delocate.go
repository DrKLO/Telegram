// Copyright (c) 2017, Google Inc.
//
// Permission to use, copy, modify, and/or distribute this software for any
// purpose with or without fee is hereby granted, provided that the above
// copyright notice and this permission notice appear in all copies.
//
// THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
// WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
// SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
// WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
// OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
// CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

// delocate performs several transformations of textual assembly code. See
// crypto/fipsmodule/FIPS.md for an overview.
package main

import (
	"errors"
	"flag"
	"fmt"
	"io/ioutil"
	"os"
	"sort"
	"strconv"
	"strings"

	"boringssl.googlesource.com/boringssl/util/ar"
	"boringssl.googlesource.com/boringssl/util/fipstools/fipscommon"
)

// inputFile represents a textual assembly file.
type inputFile struct {
	path string
	// index is a unique identifer given to this file. It's used for
	// mapping local symbols.
	index int
	// isArchive indicates that the input should be processed as an ar
	// file.
	isArchive bool
	// contents contains the contents of the file.
	contents string
	// ast points to the head of the syntax tree.
	ast *node32
}

type stringWriter interface {
	WriteString(string) (int, error)
}

type processorType int

const (
	ppc64le processorType = iota + 1
	x86_64
)

// delocation holds the state needed during a delocation operation.
type delocation struct {
	processor processorType
	output    stringWriter

	// symbols is the set of symbols defined in the module.
	symbols map[string]struct{}
	// localEntrySymbols is the set of symbols with .localentry directives.
	localEntrySymbols map[string]struct{}
	// redirectors maps from out-call symbol name to the name of a
	// redirector function for that symbol. E.g. “memcpy” ->
	// “bcm_redirector_memcpy”.
	redirectors map[string]string
	// bssAccessorsNeeded maps from a BSS symbol name to the symbol that
	// should be used to reference it. E.g. “P384_data_storage” ->
	// “P384_data_storage”.
	bssAccessorsNeeded map[string]string
	// tocLoaders is a set of symbol names for which TOC helper functions
	// are required. (ppc64le only.)
	tocLoaders map[string]struct{}
	// gotExternalsNeeded is a set of symbol names for which we need
	// “delta” symbols: symbols that contain the offset from their location
	// to the memory in question.
	gotExternalsNeeded map[string]struct{}

	currentInput inputFile
}

func (d *delocation) contents(node *node32) string {
	return d.currentInput.contents[node.begin:node.end]
}

// writeNode writes out an AST node.
func (d *delocation) writeNode(node *node32) {
	if _, err := d.output.WriteString(d.contents(node)); err != nil {
		panic(err)
	}
}

func (d *delocation) writeCommentedNode(node *node32) {
	line := d.contents(node)
	if _, err := d.output.WriteString("# WAS " + strings.TrimSpace(line) + "\n"); err != nil {
		panic(err)
	}
}

func locateError(err error, with *node32, in inputFile) error {
	posMap := translatePositions([]rune(in.contents), []int{int(with.begin)})
	var line int
	for _, pos := range posMap {
		line = pos.line
	}

	return fmt.Errorf("error while processing %q on line %d: %q", in.contents[with.begin:with.end], line, err)
}

func (d *delocation) processInput(input inputFile) (err error) {
	d.currentInput = input

	var origStatement *node32
	defer func() {
		if err := recover(); err != nil {
			panic(locateError(fmt.Errorf("%s", err), origStatement, input))
		}
	}()

	for statement := input.ast.up; statement != nil; statement = statement.next {
		assertNodeType(statement, ruleStatement)
		origStatement = statement

		node := skipWS(statement.up)
		if node == nil {
			d.writeNode(statement)
			continue
		}

		switch node.pegRule {
		case ruleGlobalDirective, ruleComment, ruleLocationDirective:
			d.writeNode(statement)
		case ruleDirective:
			statement, err = d.processDirective(statement, node.up)
		case ruleLabelContainingDirective:
			statement, err = d.processLabelContainingDirective(statement, node.up)
		case ruleLabel:
			statement, err = d.processLabel(statement, node.up)
		case ruleInstruction:
			switch d.processor {
			case x86_64:
				statement, err = d.processIntelInstruction(statement, node.up)
			case ppc64le:
				statement, err = d.processPPCInstruction(statement, node.up)
			default:
				panic("unknown processor")
			}
		default:
			panic(fmt.Sprintf("unknown top-level statement type %q", rul3s[node.pegRule]))
		}

		if err != nil {
			return locateError(err, origStatement, input)
		}
	}

	return nil
}

func (d *delocation) processDirective(statement, directive *node32) (*node32, error) {
	assertNodeType(directive, ruleDirectiveName)
	directiveName := d.contents(directive)

	var args []string
	forEachPath(directive, func(arg *node32) {
		// If the argument is a quoted string, use the raw contents.
		// (Note that this doesn't unescape the string, but that's not
		// needed so far.
		if arg.up != nil {
			arg = arg.up
			assertNodeType(arg, ruleQuotedArg)
			if arg.up == nil {
				args = append(args, "")
				return
			}
			arg = arg.up
			assertNodeType(arg, ruleQuotedText)
		}
		args = append(args, d.contents(arg))
	}, ruleArgs, ruleArg)

	switch directiveName {
	case "comm", "lcomm":
		if len(args) < 1 {
			return nil, errors.New("comm directive has no arguments")
		}
		d.bssAccessorsNeeded[args[0]] = args[0]
		d.writeNode(statement)

	case "data":
		// ASAN and some versions of MSAN are adding a .data section,
		// and adding references to symbols within it to the code. We
		// will have to work around this in the future.
		return nil, errors.New(".data section found in module")

	case "section":
		section := args[0]

		if section == ".data.rel.ro" {
			// In a normal build, this is an indication of a
			// problem but any references from the module to this
			// section will result in a relocation and thus will
			// break the integrity check. ASAN can generate these
			// sections and so we will likely have to work around
			// that in the future.
			return nil, errors.New(".data.rel.ro section found in module")
		}

		sectionType, ok := sectionType(section)
		if !ok {
			// Unknown sections are permitted in order to be robust
			// to different compiler modes.
			d.writeNode(statement)
			break
		}

		switch sectionType {
		case ".rodata", ".text":
			// Move .rodata to .text so it may be accessed without
			// a relocation. GCC with -fmerge-constants will place
			// strings into separate sections, so we move all
			// sections named like .rodata. Also move .text.startup
			// so the self-test function is also in the module.
			d.writeCommentedNode(statement)
			d.output.WriteString(".text\n")

		case ".data":
			// See above about .data
			return nil, errors.New(".data section found in module")

		case ".init_array", ".fini_array", ".ctors", ".dtors":
			// init_array/ctors/dtors contains function
			// pointers to constructor/destructor
			// functions. These contain relocations, but
			// they're in a different section anyway.
			d.writeNode(statement)
			break

		case ".debug", ".note", ".toc":
			d.writeNode(statement)
			break

		case ".bss":
			d.writeNode(statement)
			return d.handleBSS(statement)
		}

	default:
		d.writeNode(statement)
	}

	return statement, nil
}

func (d *delocation) processLabelContainingDirective(statement, directive *node32) (*node32, error) {
	// The symbols within directives need to be mapped so that local
	// symbols in two different .s inputs don't collide.
	changed := false
	assertNodeType(directive, ruleLabelContainingDirectiveName)
	name := d.contents(directive)

	node := directive.next
	assertNodeType(node, ruleWS)

	node = node.next
	assertNodeType(node, ruleSymbolArgs)

	var args []string
	for node = skipWS(node.up); node != nil; node = skipWS(node.next) {
		assertNodeType(node, ruleSymbolArg)
		arg := node.up
		var mapped string

		for term := arg; term != nil; term = term.next {
			if term.pegRule != ruleLocalSymbol {
				mapped += d.contents(term)
				continue
			}

			oldSymbol := d.contents(term)
			newSymbol := d.mapLocalSymbol(oldSymbol)
			if newSymbol != oldSymbol {
				changed = true
			}

			mapped += newSymbol
		}

		args = append(args, mapped)
	}

	if !changed {
		d.writeNode(statement)
	} else {
		d.writeCommentedNode(statement)
		d.output.WriteString("\t" + name + "\t" + strings.Join(args, ", ") + "\n")
	}

	if name == ".localentry" {
		d.output.WriteString(localEntryName(args[0]) + ":\n")
	}

	return statement, nil
}

func (d *delocation) processLabel(statement, label *node32) (*node32, error) {
	symbol := d.contents(label)

	switch label.pegRule {
	case ruleLocalLabel:
		d.output.WriteString(symbol + ":\n")
	case ruleLocalSymbol:
		// symbols need to be mapped so that local symbols from two
		// different .s inputs don't collide.
		d.output.WriteString(d.mapLocalSymbol(symbol) + ":\n")
	case ruleSymbolName:
		d.output.WriteString(localTargetName(symbol) + ":\n")
		d.writeNode(statement)
	default:
		return nil, fmt.Errorf("unknown label type %q", rul3s[label.pegRule])
	}

	return statement, nil
}

// instructionArgs collects all the arguments to an instruction.
func instructionArgs(node *node32) (argNodes []*node32) {
	for node = skipWS(node); node != nil; node = skipWS(node.next) {
		assertNodeType(node, ruleInstructionArg)
		argNodes = append(argNodes, node.up)
	}

	return argNodes
}

/* ppc64le

[PABI]: “64-Bit ELF V2 ABI Specification. Power Architecture.” March 21st,
        2017

(Also useful is “Power ISA Version 2.07 B”. Note that version three of that
document is /not/ good as that's POWER9 specific.)

ppc64le doesn't have IP-relative addressing and does a lot to work around this.
Rather than reference a PLT and GOT direction, it has a single structure called
the TOC (Table Of Contents). Within the TOC is the contents of .rodata, .data,
.got, .plt, .bss, etc sections [PABI;3.3].

A pointer to the TOC is maintained in r2 and the following pattern is used to
load the address of an element into a register:

  addis <address register>, 2, foo@toc@ha
  addi <address register>, <address register>, foo@toc@l

The “addis” instruction shifts a signed constant left 16 bits and adds the
result to its second argument, saving the result in the first argument. The
“addi” instruction does the same, but without shifting. Thus the “@toc@ha"
suffix on a symbol means “the top 16 bits of the TOC offset” and “@toc@l” means
“the bottom 16 bits of the offset”. However, note that both values are signed,
thus offsets in the top half of a 64KB chunk will have an @ha value that's one
greater than expected and a negative @l value.

The TOC is specific to a “module” (basically an executable or shared object).
This means that there's not a single TOC in a process and that r2 needs to
change as control moves between modules. Thus functions have two entry points:
the “global” entry point and the “local” entry point. Jumps from within the
same module can use the local entry while jumps from other modules must use the
global entry. The global entry establishes the correct value of r2 before
running the function and the local entry skips that code.

The global entry point for a function is defined by its label. The local entry
is a power-of-two number of bytes from the global entry, set by the
“.localentry” directive. (ppc64le instructions are always 32 bits, so an offset
of 1 or 2 bytes is treated as an offset of zero.)

In order to help the global entry code set r2 to point to the local TOC, r12 is
set to the address of the global entry point when called [PABI;2.2.1.1]. Thus
the global entry will typically use an addis+addi pair to add a known offset to
r12 and store it in r2. For example:

foo:
  addis 2, 12, .TOC. - foo@ha
  addi  2, 2,  .TOC. - foo@l

(It's worth noting that the '@' operator binds very loosely, so the 3rd
arguments parse as (.TOC. - foo)@ha and (.TOC. - foo)@l.)

When calling a function, the compiler doesn't know whether that function is in
the same module or not. Thus it doesn't know whether r12 needs to be set nor
whether r2 will be clobbered on return. Rather than always assume the worst,
the linker fixes stuff up once it knows that a call is going out of module:

Firstly, calling, say, memcpy (which we assume to be in a different module)
won't actually jump directly to memcpy, or even a PLT resolution function.
It'll call a synthesised function that:
  a) saves r2 in the caller's stack frame
  b) loads the address of memcpy@PLT into r12
  c) jumps to r12.

As this synthesised function loads memcpy@PLT, a call to memcpy from the
compiled code just references “memcpy” directly, not “memcpy@PLT”.

Since it jumps directly to memcpy@PLT, it can't restore r2 on return. Thus
calls must be followed by a nop. If the call ends up going out-of-module, the
linker will rewrite that nop to load r2 from the stack.

Speaking of the stack, the stack pointer is kept in r1 and there's a 288-byte
red-zone. The format of the stack frame is defined [PABI;2.2.2] and must be
followed as called functions will write into their parent's stack frame. For
example, the synthesised out-of-module trampolines will save r2 24 bytes into
the caller's frame and all non-leaf functions save the return address 16 bytes
into the caller's frame.

A final point worth noting: some RISC ISAs have r0 wired to zero: all reads
result in zero and all writes are discarded. POWER does something a little like
that, but r0 is only special in certain argument positions for certain
instructions. You just have to read the manual to know which they are.


Delocation is easier than Intel because there's just TOC references, but it's
also harder because there's no IP-relative addressing.

Jumps are IP-relative however, and have a 24-bit immediate value. So we can
jump to functions that set a register to the needed value. (r3 is the
return-value register and so that's what is generally used here.) */

// isPPC64LEAPair recognises an addis+addi pair that's adding the offset of
// source to relative and writing the result to target.
func (d *delocation) isPPC64LEAPair(statement *node32) (target, source, relative string, ok bool) {
	instruction := skipWS(statement.up).up
	assertNodeType(instruction, ruleInstructionName)
	name1 := d.contents(instruction)
	args1 := instructionArgs(instruction.next)

	statement = statement.next
	instruction = skipWS(statement.up).up
	assertNodeType(instruction, ruleInstructionName)
	name2 := d.contents(instruction)
	args2 := instructionArgs(instruction.next)

	if name1 != "addis" ||
		len(args1) != 3 ||
		name2 != "addi" ||
		len(args2) != 3 {
		return "", "", "", false
	}

	target = d.contents(args1[0])
	relative = d.contents(args1[1])
	source1 := d.contents(args1[2])
	source2 := d.contents(args2[2])

	if !strings.HasSuffix(source1, "@ha") ||
		!strings.HasSuffix(source2, "@l") ||
		source1[:len(source1)-3] != source2[:len(source2)-2] ||
		d.contents(args2[0]) != target ||
		d.contents(args2[1]) != target {
		return "", "", "", false
	}

	source = source1[:len(source1)-3]
	ok = true
	return
}

// establishTOC writes the global entry prelude for a function. The standard
// prelude involves relocations so this version moves the relocation outside
// the integrity-checked area.
func establishTOC(w stringWriter) {
	w.WriteString("999:\n")
	w.WriteString("\taddis 2, 12, .LBORINGSSL_external_toc-999b@ha\n")
	w.WriteString("\taddi 2, 2, .LBORINGSSL_external_toc-999b@l\n")
	w.WriteString("\tld 12, 0(2)\n")
	w.WriteString("\tadd 2, 2, 12\n")
}

// loadTOCFuncName returns the name of a synthesized function that sets r3 to
// the value of “symbol+offset”.
func loadTOCFuncName(symbol, offset string) string {
	symbol = strings.Replace(symbol, ".", "_dot_", -1)
	ret := ".Lbcm_loadtoc_" + symbol
	if len(offset) != 0 {
		offset = strings.Replace(offset, "+", "_plus_", -1)
		offset = strings.Replace(offset, "-", "_minus_", -1)
		ret += "_" + offset
	}
	return ret
}

func (d *delocation) loadFromTOC(w stringWriter, symbol, offset, dest string) wrapperFunc {
	d.tocLoaders[symbol+"\x00"+offset] = struct{}{}

	return func(k func()) {
		w.WriteString("\taddi 1, 1, -288\n")   // Clear the red zone.
		w.WriteString("\tmflr " + dest + "\n") // Stash the link register.
		w.WriteString("\tstd " + dest + ", -8(1)\n")
		// The TOC loader will use r3, so stash it if necessary.
		if dest != "3" {
			w.WriteString("\tstd 3, -16(1)\n")
		}

		// Because loadTOCFuncName returns a “.L” name, we don't need a
		// nop after this call.
		w.WriteString("\tbl " + loadTOCFuncName(symbol, offset) + "\n")

		// Cycle registers around. We need r3 -> destReg, -8(1) ->
		// lr and, optionally, -16(1) -> r3.
		w.WriteString("\tstd 3, -24(1)\n")
		w.WriteString("\tld 3, -8(1)\n")
		w.WriteString("\tmtlr 3\n")
		w.WriteString("\tld " + dest + ", -24(1)\n")
		if dest != "3" {
			w.WriteString("\tld 3, -16(1)\n")
		}
		w.WriteString("\taddi 1, 1, 288\n")

		k()
	}
}

func (d *delocation) gatherOffsets(symRef *node32, offsets string) (*node32, string) {
	for symRef != nil && symRef.pegRule == ruleOffset {
		offset := d.contents(symRef)
		if offset[0] != '+' && offset[0] != '-' {
			offset = "+" + offset
		}
		offsets = offsets + offset
		symRef = symRef.next
	}
	return symRef, offsets
}

func (d *delocation) parseMemRef(memRef *node32) (symbol, offset, section string, didChange, symbolIsLocal bool, nextRef *node32) {
	if memRef.pegRule != ruleSymbolRef {
		return "", "", "", false, false, memRef
	}

	symRef := memRef.up
	nextRef = memRef.next

	// (Offset* '+')?
	symRef, offset = d.gatherOffsets(symRef, offset)

	// (LocalSymbol / SymbolName)
	symbol = d.contents(symRef)
	if symRef.pegRule == ruleLocalSymbol {
		symbolIsLocal = true
		mapped := d.mapLocalSymbol(symbol)
		if mapped != symbol {
			symbol = mapped
			didChange = true
		}
	}
	symRef = symRef.next

	// Offset*
	symRef, offset = d.gatherOffsets(symRef, offset)

	// ('@' Section / Offset*)?
	if symRef != nil {
		assertNodeType(symRef, ruleSection)
		section = d.contents(symRef)
		symRef = symRef.next

		symRef, offset = d.gatherOffsets(symRef, offset)
	}

	if symRef != nil {
		panic(fmt.Sprintf("unexpected token in SymbolRef: %q", rul3s[symRef.pegRule]))
	}

	return
}

func (d *delocation) processPPCInstruction(statement, instruction *node32) (*node32, error) {
	assertNodeType(instruction, ruleInstructionName)
	instructionName := d.contents(instruction)
	isBranch := instructionName[0] == 'b'

	argNodes := instructionArgs(instruction.next)

	var wrappers wrapperStack
	var args []string
	changed := false

Args:
	for i, arg := range argNodes {
		fullArg := arg
		isIndirect := false

		if arg.pegRule == ruleIndirectionIndicator {
			arg = arg.next
			isIndirect = true
		}

		switch arg.pegRule {
		case ruleRegisterOrConstant, ruleLocalLabelRef:
			args = append(args, d.contents(fullArg))

		case ruleTOCRefLow:
			return nil, errors.New("Found low TOC reference outside preamble pattern")

		case ruleTOCRefHigh:
			target, _, relative, ok := d.isPPC64LEAPair(statement)
			if !ok {
				return nil, errors.New("Found high TOC reference outside preamble pattern")
			}

			if relative != "12" {
				return nil, fmt.Errorf("preamble is relative to %q, not r12", relative)
			}

			if target != "2" {
				return nil, fmt.Errorf("preamble is setting %q, not r2", target)
			}

			statement = statement.next
			establishTOC(d.output)
			instructionName = ""
			changed = true
			break Args

		case ruleMemoryRef:
			symbol, offset, section, didChange, symbolIsLocal, memRef := d.parseMemRef(arg.up)
			changed = didChange

			if len(symbol) > 0 {
				if _, localEntrySymbol := d.localEntrySymbols[symbol]; localEntrySymbol && isBranch {
					symbol = localEntryName(symbol)
					changed = true
				} else if _, knownSymbol := d.symbols[symbol]; knownSymbol {
					symbol = localTargetName(symbol)
					changed = true
				} else if !symbolIsLocal && !isSynthesized(symbol) && len(section) == 0 {
					changed = true
					d.redirectors[symbol] = redirectorName(symbol)
					symbol = redirectorName(symbol)
					// TODO(davidben): This should sanity-check the next
					// instruction is a nop and ideally remove it.
					wrappers = append(wrappers, func(k func()) {
						k()
						// Like the linker's PLT stubs, redirector functions
						// expect callers to restore r2.
						d.output.WriteString("\tld 2, 24(1)\n")
					})
				}
			}

			switch section {
			case "":

			case "tls":
				// This section identifier just tells the
				// assembler to use r13, the pointer to the
				// thread-local data [PABI;3.7.3.3].

			case "toc@ha":
				// Delete toc@ha instructions. Per
				// [PABI;3.6.3], the linker is allowed to erase
				// toc@ha instructions. We take advantage of
				// this by unconditionally erasing the toc@ha
				// instructions and doing the full lookup when
				// processing toc@l.
				//
				// Note that any offset here applies before @ha
				// and @l. That is, 42+foo@toc@ha is
				// #ha(42+foo-.TOC.), not 42+#ha(foo-.TOC.). Any
				// corresponding toc@l references are required
				// by the ABI to have the same offset. The
				// offset will be incorporated in full when
				// those are processed.
				if instructionName != "addis" || len(argNodes) != 3 || i != 2 || args[1] != "2" {
					return nil, errors.New("can't process toc@ha reference")
				}
				changed = true
				instructionName = ""
				break Args

			case "toc@l":
				// Per [PAB;3.6.3], this instruction must take
				// as input a register which was the output of
				// a toc@ha computation and compute the actual
				// address of some symbol. The toc@ha
				// computation was elided, so we ignore that
				// input register and compute the address
				// directly.
				changed = true

				// For all supported toc@l instructions, the
				// destination register is the first argument.
				destReg := args[0]

				wrappers = append(wrappers, d.loadFromTOC(d.output, symbol, offset, destReg))
				switch instructionName {
				case "addi":
					// The original instruction was:
					//   addi destReg, tocHaReg, offset+symbol@toc@l
					instructionName = ""

				case "ld", "lhz", "lwz":
					// The original instruction was:
					//   l?? destReg, offset+symbol@toc@l(tocHaReg)
					//
					// We transform that into the
					// equivalent dereference of destReg:
					//   l?? destReg, 0(destReg)
					origInstructionName := instructionName
					instructionName = ""

					assertNodeType(memRef, ruleBaseIndexScale)
					assertNodeType(memRef.up, ruleRegisterOrConstant)
					if memRef.next != nil || memRef.up.next != nil {
						return nil, errors.New("expected single register in BaseIndexScale for ld argument")
					}

					baseReg := destReg
					if baseReg == "0" {
						// Register zero is special as the base register for a load.
						// Avoid it by spilling and using r3 instead.
						baseReg = "3"
						wrappers = append(wrappers, func(k func()) {
							d.output.WriteString("\taddi 1, 1, -288\n") // Clear the red zone.
							d.output.WriteString("\tstd " + baseReg + ", -8(1)\n")
							d.output.WriteString("\tmr " + baseReg + ", " + destReg + "\n")
							k()
							d.output.WriteString("\tld " + baseReg + ", -8(1)\n")
							d.output.WriteString("\taddi 1, 1, 288\n") // Clear the red zone.
						})
					}

					wrappers = append(wrappers, func(k func()) {
						d.output.WriteString("\t" + origInstructionName + " " + destReg + ", 0(" + baseReg + ")\n")
					})
				default:
					return nil, fmt.Errorf("can't process TOC argument to %q", instructionName)
				}

			default:
				return nil, fmt.Errorf("Unknown section type %q", section)
			}

			argStr := ""
			if isIndirect {
				argStr += "*"
			}
			argStr += symbol
			if len(offset) > 0 {
				argStr += offset
			}
			if len(section) > 0 {
				argStr += "@"
				argStr += section
			}

			for ; memRef != nil; memRef = memRef.next {
				argStr += d.contents(memRef)
			}

			args = append(args, argStr)

		default:
			panic(fmt.Sprintf("unknown instruction argument type %q", rul3s[arg.pegRule]))
		}
	}

	if changed {
		d.writeCommentedNode(statement)

		var replacement string
		if len(instructionName) > 0 {
			replacement = "\t" + instructionName + "\t" + strings.Join(args, ", ") + "\n"
		}

		wrappers.do(func() {
			d.output.WriteString(replacement)
		})
	} else {
		d.writeNode(statement)
	}

	return statement, nil
}

/* Intel */

type instructionType int

const (
	instrPush instructionType = iota
	instrMove
	// instrTransformingMove is essentially a move, but it performs some
	// transformation of the data during the process.
	instrTransformingMove
	instrJump
	instrConditionalMove
	// instrCombine merges the source and destination in some fashion, for example
	// a 2-operand bitwise operation.
	instrCombine
	// instrThreeArg merges two sources into a destination in some fashion.
	instrThreeArg
	instrOther
)

func classifyInstruction(instr string, args []*node32) instructionType {
	switch instr {
	case "push", "pushq":
		if len(args) == 1 {
			return instrPush
		}

	case "mov", "movq", "vmovq", "movsd", "vmovsd":
		if len(args) == 2 {
			return instrMove
		}

	case "cmovneq", "cmoveq":
		if len(args) == 2 {
			return instrConditionalMove
		}

	case "call", "callq", "jmp", "jo", "jno", "js", "jns", "je", "jz", "jne", "jnz", "jb", "jnae", "jc", "jnb", "jae", "jnc", "jbe", "jna", "ja", "jnbe", "jl", "jnge", "jge", "jnl", "jle", "jng", "jg", "jnle", "jp", "jpe", "jnp", "jpo":
		if len(args) == 1 {
			return instrJump
		}

	case "orq", "andq", "xorq":
		if len(args) == 2 {
			return instrCombine
		}

	case "sarxq", "shlxq", "shrxq":
		if len(args) == 3 {
			return instrThreeArg
		}

	case "vpbroadcastq":
		if len(args) == 2 {
			return instrTransformingMove
		}
	}

	return instrOther
}

func push(w stringWriter) wrapperFunc {
	return func(k func()) {
		w.WriteString("\tpushq %rax\n")
		k()
		w.WriteString("\txchg %rax, (%rsp)\n")
	}
}

func (d *delocation) loadFromGOT(w stringWriter, destination, symbol, section string, redzoneCleared bool) wrapperFunc {
	d.gotExternalsNeeded[symbol+"@"+section] = struct{}{}

	return func(k func()) {
		if !redzoneCleared {
			w.WriteString("\tleaq -128(%rsp), %rsp\n") // Clear the red zone.
		}
		w.WriteString("\tpushf\n")
		w.WriteString(fmt.Sprintf("\tleaq %s_%s_external(%%rip), %s\n", symbol, section, destination))
		w.WriteString(fmt.Sprintf("\taddq (%s), %s\n", destination, destination))
		w.WriteString(fmt.Sprintf("\tmovq (%s), %s\n", destination, destination))
		w.WriteString("\tpopf\n")
		if !redzoneCleared {
			w.WriteString("\tleaq\t128(%rsp), %rsp\n")
		}
	}
}

func saveFlags(w stringWriter, redzoneCleared bool) wrapperFunc {
	return func(k func()) {
		if !redzoneCleared {
			w.WriteString("\tleaq -128(%rsp), %rsp\n") // Clear the red zone.
			defer w.WriteString("\tleaq 128(%rsp), %rsp\n")
		}
		w.WriteString("\tpushfq\n")
		k()
		w.WriteString("\tpopfq\n")
	}
}

func saveRegister(w stringWriter, avoidRegs []string) (wrapperFunc, string) {
	candidates := []string{"%rax", "%rbx", "%rcx", "%rdx"}

	var reg string
NextCandidate:
	for _, candidate := range candidates {
		for _, avoid := range avoidRegs {
			if candidate == avoid {
				continue NextCandidate
			}
		}

		reg = candidate
		break
	}

	if len(reg) == 0 {
		panic("too many excluded registers")
	}

	return func(k func()) {
		w.WriteString("\tleaq -128(%rsp), %rsp\n") // Clear the red zone.
		w.WriteString("\tpushq " + reg + "\n")
		k()
		w.WriteString("\tpopq " + reg + "\n")
		w.WriteString("\tleaq 128(%rsp), %rsp\n")
	}, reg
}

func moveTo(w stringWriter, target string, isAVX bool, source string) wrapperFunc {
	return func(k func()) {
		k()
		prefix := ""
		if isAVX {
			prefix = "v"
		}
		w.WriteString("\t" + prefix + "movq " + source + ", " + target + "\n")
	}
}

func finalTransform(w stringWriter, transformInstruction, reg string) wrapperFunc {
	return func(k func()) {
		k()
		w.WriteString("\t" + transformInstruction + " " + reg + ", " + reg + "\n")
	}
}

func combineOp(w stringWriter, instructionName, source, dest string) wrapperFunc {
	return func(k func()) {
		k()
		w.WriteString("\t" + instructionName + " " + source + ", " + dest + "\n")
	}
}

func threeArgCombineOp(w stringWriter, instructionName, source1, source2, dest string) wrapperFunc {
	return func(k func()) {
		k()
		w.WriteString("\t" + instructionName + " " + source1 + ", " + source2 + ", " + dest + "\n")
	}
}

func isValidLEATarget(reg string) bool {
	return !strings.HasPrefix(reg, "%xmm") && !strings.HasPrefix(reg, "%ymm") && !strings.HasPrefix(reg, "%zmm")
}

func undoConditionalMove(w stringWriter, instr string) wrapperFunc {
	var invertedCondition string

	switch instr {
	case "cmoveq":
		invertedCondition = "ne"
	case "cmovneq":
		invertedCondition = "e"
	default:
		panic(fmt.Sprintf("don't know how to handle conditional move instruction %q", instr))
	}

	return func(k func()) {
		w.WriteString("\tj" + invertedCondition + " 999f\n")
		k()
		w.WriteString("999:\n")
	}
}

func (d *delocation) isRIPRelative(node *node32) bool {
	return node != nil && node.pegRule == ruleBaseIndexScale && d.contents(node) == "(%rip)"
}

func (d *delocation) processIntelInstruction(statement, instruction *node32) (*node32, error) {
	assertNodeType(instruction, ruleInstructionName)
	instructionName := d.contents(instruction)

	argNodes := instructionArgs(instruction.next)

	var wrappers wrapperStack
	var args []string
	changed := false

Args:
	for i, arg := range argNodes {
		fullArg := arg
		isIndirect := false

		if arg.pegRule == ruleIndirectionIndicator {
			arg = arg.next
			isIndirect = true
		}

		switch arg.pegRule {
		case ruleRegisterOrConstant, ruleLocalLabelRef:
			args = append(args, d.contents(fullArg))

		case ruleMemoryRef:
			symbol, offset, section, didChange, symbolIsLocal, memRef := d.parseMemRef(arg.up)
			changed = didChange

			if symbol == "OPENSSL_ia32cap_P" && section == "" {
				if instructionName != "leaq" {
					return nil, fmt.Errorf("non-leaq instruction %q referenced OPENSSL_ia32cap_P directly", instructionName)
				}

				if i != 0 || len(argNodes) != 2 || !d.isRIPRelative(memRef) || len(offset) > 0 {
					return nil, fmt.Errorf("invalid OPENSSL_ia32cap_P reference in instruction %q", instructionName)
				}

				target := argNodes[1]
				assertNodeType(target, ruleRegisterOrConstant)
				reg := d.contents(target)

				if !strings.HasPrefix(reg, "%r") {
					return nil, fmt.Errorf("tried to load OPENSSL_ia32cap_P into %q, which is not a standard register.", reg)
				}

				changed = true

				// Flag-altering instructions (i.e. addq) are going to be used so the
				// flags need to be preserved.
				wrappers = append(wrappers, saveFlags(d.output, false /* Red Zone not yet cleared */))

				wrappers = append(wrappers, func(k func()) {
					d.output.WriteString("\tleaq\tOPENSSL_ia32cap_addr_delta(%rip), " + reg + "\n")
					d.output.WriteString("\taddq\t(" + reg + "), " + reg + "\n")
				})

				break Args
			}

			switch section {
			case "":
				if _, knownSymbol := d.symbols[symbol]; knownSymbol {
					symbol = localTargetName(symbol)
					changed = true
				}

			case "PLT":
				if classifyInstruction(instructionName, argNodes) != instrJump {
					return nil, fmt.Errorf("Cannot rewrite PLT reference for non-jump instruction %q", instructionName)
				}

				if _, knownSymbol := d.symbols[symbol]; knownSymbol {
					symbol = localTargetName(symbol)
					changed = true
				} else if !symbolIsLocal && !isSynthesized(symbol) {
					// Unknown symbol via PLT is an
					// out-call from the module, e.g.
					// memcpy.
					d.redirectors[symbol+"@"+section] = redirectorName(symbol)
					symbol = redirectorName(symbol)
				}

				changed = true

			case "GOTPCREL":
				if len(offset) > 0 {
					return nil, errors.New("loading from GOT with offset is unsupported")
				}
				if !d.isRIPRelative(memRef) {
					return nil, errors.New("GOT access must be IP-relative")
				}

				useGOT := false
				if _, knownSymbol := d.symbols[symbol]; knownSymbol {
					symbol = localTargetName(symbol)
					changed = true
				} else if !isSynthesized(symbol) {
					useGOT = true
				}

				classification := classifyInstruction(instructionName, argNodes)
				if classification != instrThreeArg && i != 0 {
					return nil, errors.New("GOT access must be source operand")
				}

				// Reduce the instruction to movq symbol@GOTPCREL, targetReg.
				var targetReg string
				var redzoneCleared bool
				switch classification {
				case instrPush:
					wrappers = append(wrappers, push(d.output))
					targetReg = "%rax"
				case instrConditionalMove:
					wrappers = append(wrappers, undoConditionalMove(d.output, instructionName))
					fallthrough
				case instrMove:
					assertNodeType(argNodes[1], ruleRegisterOrConstant)
					targetReg = d.contents(argNodes[1])
				case instrTransformingMove:
					assertNodeType(argNodes[1], ruleRegisterOrConstant)
					targetReg = d.contents(argNodes[1])
					wrappers = append(wrappers, finalTransform(d.output, instructionName, targetReg))
					if isValidLEATarget(targetReg) {
						return nil, errors.New("Currently transforming moves are assumed to target XMM registers. Otherwise we'll pop %rax before reading it to do the transform.")
					}
				case instrCombine:
					targetReg = d.contents(argNodes[1])
					if !isValidLEATarget(targetReg) {
						return nil, fmt.Errorf("cannot handle combining instructions targeting non-general registers")
					}
					saveRegWrapper, tempReg := saveRegister(d.output, []string{targetReg})
					redzoneCleared = true
					wrappers = append(wrappers, saveRegWrapper)

					wrappers = append(wrappers, combineOp(d.output, instructionName, tempReg, targetReg))
					targetReg = tempReg
				case instrThreeArg:
					if n := len(argNodes); n != 3 {
						return nil, fmt.Errorf("three-argument instruction has %d arguments", n)
					}
					if i != 0 && i != 1 {
						return nil, errors.New("GOT access must be from soure operand")
					}
					targetReg = d.contents(argNodes[2])

					otherSource := d.contents(argNodes[1])
					if i == 1 {
						otherSource = d.contents(argNodes[0])
					}

					saveRegWrapper, tempReg := saveRegister(d.output, []string{targetReg, otherSource})
					redzoneCleared = true
					wrappers = append(wrappers, saveRegWrapper)

					if i == 0 {
						wrappers = append(wrappers, threeArgCombineOp(d.output, instructionName, tempReg, otherSource, targetReg))
					} else {
						wrappers = append(wrappers, threeArgCombineOp(d.output, instructionName, otherSource, tempReg, targetReg))
					}
					targetReg = tempReg
				default:
					return nil, fmt.Errorf("Cannot rewrite GOTPCREL reference for instruction %q", instructionName)
				}

				if !isValidLEATarget(targetReg) {
					// Sometimes the compiler will load from the GOT to an
					// XMM register, which is not a valid target of an LEA
					// instruction.
					saveRegWrapper, tempReg := saveRegister(d.output, nil)
					wrappers = append(wrappers, saveRegWrapper)
					isAVX := strings.HasPrefix(instructionName, "v")
					wrappers = append(wrappers, moveTo(d.output, targetReg, isAVX, tempReg))
					targetReg = tempReg
					if redzoneCleared {
						return nil, fmt.Errorf("internal error: Red Zone was already cleared")
					}
					redzoneCleared = true
				}

				if symbol == "OPENSSL_ia32cap_P" {
					// Flag-altering instructions (i.e. addq) are going to be used so the
					// flags need to be preserved.
					wrappers = append(wrappers, saveFlags(d.output, redzoneCleared))
					wrappers = append(wrappers, func(k func()) {
						d.output.WriteString("\tleaq\tOPENSSL_ia32cap_addr_delta(%rip), " + targetReg + "\n")
						d.output.WriteString("\taddq\t(" + targetReg + "), " + targetReg + "\n")
					})
				} else if useGOT {
					wrappers = append(wrappers, d.loadFromGOT(d.output, targetReg, symbol, section, redzoneCleared))
				} else {
					wrappers = append(wrappers, func(k func()) {
						d.output.WriteString(fmt.Sprintf("\tleaq\t%s(%%rip), %s\n", symbol, targetReg))
					})
				}
				changed = true
				break Args

			default:
				return nil, fmt.Errorf("Unknown section type %q", section)
			}

			if !changed && len(section) > 0 {
				panic("section was not handled")
			}
			section = ""

			argStr := ""
			if isIndirect {
				argStr += "*"
			}
			argStr += symbol
			argStr += offset

			for ; memRef != nil; memRef = memRef.next {
				argStr += d.contents(memRef)
			}

			args = append(args, argStr)

		default:
			panic(fmt.Sprintf("unknown instruction argument type %q", rul3s[arg.pegRule]))
		}
	}

	if changed {
		d.writeCommentedNode(statement)
		replacement := "\t" + instructionName + "\t" + strings.Join(args, ", ") + "\n"
		wrappers.do(func() {
			d.output.WriteString(replacement)
		})
	} else {
		d.writeNode(statement)
	}

	return statement, nil
}

func (d *delocation) handleBSS(statement *node32) (*node32, error) {
	lastStatement := statement
	for statement = statement.next; statement != nil; lastStatement, statement = statement, statement.next {
		node := skipWS(statement.up)
		if node == nil {
			d.writeNode(statement)
			continue
		}

		switch node.pegRule {
		case ruleGlobalDirective, ruleComment, ruleInstruction, ruleLocationDirective:
			d.writeNode(statement)

		case ruleDirective:
			directive := node.up
			assertNodeType(directive, ruleDirectiveName)
			directiveName := d.contents(directive)
			if directiveName == "text" || directiveName == "section" || directiveName == "data" {
				return lastStatement, nil
			}
			d.writeNode(statement)

		case ruleLabel:
			label := node.up
			d.writeNode(statement)

			if label.pegRule != ruleLocalSymbol {
				symbol := d.contents(label)
				localSymbol := localTargetName(symbol)
				d.output.WriteString(fmt.Sprintf("\n%s:\n", localSymbol))

				d.bssAccessorsNeeded[symbol] = localSymbol
			}

		case ruleLabelContainingDirective:
			var err error
			statement, err = d.processLabelContainingDirective(statement, node.up)
			if err != nil {
				return nil, err
			}

		default:
			return nil, fmt.Errorf("unknown BSS statement type %q in %q", rul3s[node.pegRule], d.contents(statement))
		}
	}

	return lastStatement, nil
}

func transform(w stringWriter, inputs []inputFile) error {
	// symbols contains all defined symbols.
	symbols := make(map[string]struct{})
	// localEntrySymbols contains all symbols with a .localentry directive.
	localEntrySymbols := make(map[string]struct{})
	// fileNumbers is the set of IDs seen in .file directives.
	fileNumbers := make(map[int]struct{})
	// maxObservedFileNumber contains the largest seen file number in a
	// .file directive. Zero is not a valid number.
	maxObservedFileNumber := 0
	// fileDirectivesContainMD5 is true if the compiler is outputting MD5
	// checksums in .file directives. If it does so, then this script needs
	// to match that behaviour otherwise warnings result.
	fileDirectivesContainMD5 := false

	// OPENSSL_ia32cap_get will be synthesized by this script.
	symbols["OPENSSL_ia32cap_get"] = struct{}{}

	for _, input := range inputs {
		forEachPath(input.ast.up, func(node *node32) {
			symbol := input.contents[node.begin:node.end]
			if _, ok := symbols[symbol]; ok {
				panic(fmt.Sprintf("Duplicate symbol found: %q in %q", symbol, input.path))
			}
			symbols[symbol] = struct{}{}
		}, ruleStatement, ruleLabel, ruleSymbolName)

		forEachPath(input.ast.up, func(node *node32) {
			node = node.up
			assertNodeType(node, ruleLabelContainingDirectiveName)
			directive := input.contents[node.begin:node.end]
			if directive != ".localentry" {
				return
			}
			// Extract the first argument.
			node = skipWS(node.next)
			assertNodeType(node, ruleSymbolArgs)
			node = node.up
			assertNodeType(node, ruleSymbolArg)
			symbol := input.contents[node.begin:node.end]
			if _, ok := localEntrySymbols[symbol]; ok {
				panic(fmt.Sprintf("Duplicate .localentry directive found: %q in %q", symbol, input.path))
			}
			localEntrySymbols[symbol] = struct{}{}
		}, ruleStatement, ruleLabelContainingDirective)

		forEachPath(input.ast.up, func(node *node32) {
			assertNodeType(node, ruleLocationDirective)
			directive := input.contents[node.begin:node.end]
			if !strings.HasPrefix(directive, ".file") {
				return
			}
			parts := strings.Fields(directive)
			if len(parts) == 2 {
				// This is a .file directive with just a
				// filename. Clang appears to generate just one
				// of these at the beginning of the output for
				// the compilation unit. Ignore it.
				return
			}
			fileNo, err := strconv.Atoi(parts[1])
			if err != nil {
				panic(fmt.Sprintf("Failed to parse file number from .file: %q", directive))
			}

			if _, ok := fileNumbers[fileNo]; ok {
				panic(fmt.Sprintf("Duplicate file number %d observed", fileNo))
			}
			fileNumbers[fileNo] = struct{}{}

			if fileNo > maxObservedFileNumber {
				maxObservedFileNumber = fileNo
			}

			for _, token := range parts[2:] {
				if token == "md5" {
					fileDirectivesContainMD5 = true
				}
			}
		}, ruleStatement, ruleLocationDirective)
	}

	processor := x86_64
	if len(inputs) > 0 {
		processor = detectProcessor(inputs[0])
	}

	d := &delocation{
		symbols:            symbols,
		localEntrySymbols:  localEntrySymbols,
		processor:          processor,
		output:             w,
		redirectors:        make(map[string]string),
		bssAccessorsNeeded: make(map[string]string),
		tocLoaders:         make(map[string]struct{}),
		gotExternalsNeeded: make(map[string]struct{}),
	}

	w.WriteString(".text\n")
	var fileTrailing string
	if fileDirectivesContainMD5 {
		fileTrailing = " md5 0x00000000000000000000000000000000"
	}
	w.WriteString(fmt.Sprintf(".file %d \"inserted_by_delocate.c\"%s\n", maxObservedFileNumber+1, fileTrailing))
	w.WriteString(fmt.Sprintf(".loc %d 1 0\n", maxObservedFileNumber+1))
	w.WriteString("BORINGSSL_bcm_text_start:\n")

	for _, input := range inputs {
		if err := d.processInput(input); err != nil {
			return err
		}
	}

	w.WriteString(".text\n")
	w.WriteString(fmt.Sprintf(".loc %d 2 0\n", maxObservedFileNumber+1))
	w.WriteString("BORINGSSL_bcm_text_end:\n")

	// Emit redirector functions. Each is a single jump instruction.
	var redirectorNames []string
	for name := range d.redirectors {
		redirectorNames = append(redirectorNames, name)
	}
	sort.Strings(redirectorNames)

	for _, name := range redirectorNames {
		redirector := d.redirectors[name]
		if d.processor == ppc64le {
			w.WriteString(".section \".toc\", \"aw\"\n")
			w.WriteString(".Lredirector_toc_" + name + ":\n")
			w.WriteString(".quad " + name + "\n")
			w.WriteString(".text\n")
			w.WriteString(".type " + redirector + ", @function\n")
			w.WriteString(redirector + ":\n")
			// |name| will clobber r2, so save it. This is matched by a restore in
			// redirector calls.
			w.WriteString("\tstd 2, 24(1)\n")
			// Load and call |name|'s global entry point.
			w.WriteString("\taddis 12, 2, .Lredirector_toc_" + name + "@toc@ha\n")
			w.WriteString("\tld 12, .Lredirector_toc_" + name + "@toc@l(12)\n")
			w.WriteString("\tmtctr 12\n")
			w.WriteString("\tbctr\n")
		} else {
			w.WriteString(".type " + redirector + ", @function\n")
			w.WriteString(redirector + ":\n")
			w.WriteString("\tjmp\t" + name + "\n")
		}
	}

	var accessorNames []string
	for accessor := range d.bssAccessorsNeeded {
		accessorNames = append(accessorNames, accessor)
	}
	sort.Strings(accessorNames)

	// Emit BSS accessor functions. Each is a single LEA followed by RET.
	for _, name := range accessorNames {
		funcName := accessorName(name)
		w.WriteString(".type " + funcName + ", @function\n")
		w.WriteString(funcName + ":\n")
		target := d.bssAccessorsNeeded[name]

		if d.processor == ppc64le {
			w.WriteString("\taddis 3, 2, " + target + "@toc@ha\n")
			w.WriteString("\taddi 3, 3, " + target + "@toc@l\n")
			w.WriteString("\tblr\n")
		} else {
			w.WriteString("\tleaq\t" + target + "(%rip), %rax\n\tret\n")
		}
	}

	if d.processor == ppc64le {
		loadTOCNames := sortedSet(d.tocLoaders)
		for _, symbolAndOffset := range loadTOCNames {
			parts := strings.SplitN(symbolAndOffset, "\x00", 2)
			symbol, offset := parts[0], parts[1]

			funcName := loadTOCFuncName(symbol, offset)
			ref := symbol + offset

			w.WriteString(".type " + funcName[2:] + ", @function\n")
			w.WriteString(funcName[2:] + ":\n")
			w.WriteString(funcName + ":\n")
			w.WriteString("\taddis 3, 2, " + ref + "@toc@ha\n")
			w.WriteString("\taddi 3, 3, " + ref + "@toc@l\n")
			w.WriteString("\tblr\n")
		}

		w.WriteString(".LBORINGSSL_external_toc:\n")
		w.WriteString(".quad .TOC.-.LBORINGSSL_external_toc\n")
	} else {
		externalNames := sortedSet(d.gotExternalsNeeded)
		for _, name := range externalNames {
			parts := strings.SplitN(name, "@", 2)
			symbol, section := parts[0], parts[1]
			w.WriteString(".type " + symbol + "_" + section + "_external, @object\n")
			w.WriteString(".size " + symbol + "_" + section + "_external, 8\n")
			w.WriteString(symbol + "_" + section + "_external:\n")
			// Ideally this would be .quad foo@GOTPCREL, but clang's
			// assembler cannot emit a 64-bit GOTPCREL relocation. Instead,
			// we manually sign-extend the value, knowing that the GOT is
			// always at the end, thus foo@GOTPCREL has a positive value.
			w.WriteString("\t.long " + symbol + "@" + section + "\n")
			w.WriteString("\t.long 0\n")
		}

		w.WriteString(".type OPENSSL_ia32cap_get, @function\n")
		w.WriteString(".globl OPENSSL_ia32cap_get\n")
		w.WriteString(localTargetName("OPENSSL_ia32cap_get") + ":\n")
		w.WriteString("OPENSSL_ia32cap_get:\n")
		w.WriteString("\tleaq OPENSSL_ia32cap_P(%rip), %rax\n")
		w.WriteString("\tret\n")

		w.WriteString(".extern OPENSSL_ia32cap_P\n")
		w.WriteString(".type OPENSSL_ia32cap_addr_delta, @object\n")
		w.WriteString(".size OPENSSL_ia32cap_addr_delta, 8\n")
		w.WriteString("OPENSSL_ia32cap_addr_delta:\n")
		w.WriteString(".quad OPENSSL_ia32cap_P-OPENSSL_ia32cap_addr_delta\n")
	}

	w.WriteString(".type BORINGSSL_bcm_text_hash, @object\n")
	w.WriteString(".size BORINGSSL_bcm_text_hash, 64\n")
	w.WriteString("BORINGSSL_bcm_text_hash:\n")
	for _, b := range fipscommon.UninitHashValue {
		w.WriteString(".byte 0x" + strconv.FormatUint(uint64(b), 16) + "\n")
	}

	return nil
}

func parseInputs(inputs []inputFile) error {
	for i, input := range inputs {
		var contents string

		if input.isArchive {
			arFile, err := os.Open(input.path)
			if err != nil {
				return err
			}
			defer arFile.Close()

			ar, err := ar.ParseAR(arFile)
			if err != nil {
				return err
			}

			if len(ar) != 1 {
				return fmt.Errorf("expected one file in archive, but found %d", len(ar))
			}

			for _, c := range ar {
				contents = string(c)
			}
		} else {
			inBytes, err := ioutil.ReadFile(input.path)
			if err != nil {
				return err
			}

			contents = string(inBytes)
		}

		asm := Asm{Buffer: contents, Pretty: true}
		asm.Init()
		if err := asm.Parse(); err != nil {
			return fmt.Errorf("error while parsing %q: %s", input.path, err)
		}
		ast := asm.AST()

		inputs[i].contents = contents
		inputs[i].ast = ast
	}

	return nil
}

func main() {
	// The .a file, if given, is expected to be an archive of textual
	// assembly sources. That's odd, but CMake really wants to create
	// archive files so it's the only way that we can make it work.
	arInput := flag.String("a", "", "Path to a .a file containing assembly sources")
	outFile := flag.String("o", "", "Path to output assembly")

	flag.Parse()

	if len(*outFile) == 0 {
		fmt.Fprintf(os.Stderr, "Must give argument to -o.\n")
		os.Exit(1)
	}

	var inputs []inputFile
	if len(*arInput) > 0 {
		inputs = append(inputs, inputFile{
			path:      *arInput,
			index:     0,
			isArchive: true,
		})
	}

	for i, path := range flag.Args() {
		if len(path) == 0 {
			continue
		}

		inputs = append(inputs, inputFile{
			path:  path,
			index: i + 1,
		})
	}

	if err := parseInputs(inputs); err != nil {
		fmt.Fprintf(os.Stderr, "%s\n", err)
		os.Exit(1)
	}

	out, err := os.OpenFile(*outFile, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0644)
	if err != nil {
		panic(err)
	}
	defer out.Close()

	if err := transform(out, inputs); err != nil {
		fmt.Fprintf(os.Stderr, "%s\n", err)
		os.Exit(1)
	}
}

func forEachPath(node *node32, cb func(*node32), rules ...pegRule) {
	if node == nil {
		return
	}

	if len(rules) == 0 {
		cb(node)
		return
	}

	rule := rules[0]
	childRules := rules[1:]

	for ; node != nil; node = node.next {
		if node.pegRule != rule {
			continue
		}

		if len(childRules) == 0 {
			cb(node)
		} else {
			forEachPath(node.up, cb, childRules...)
		}
	}
}

func skipNodes(node *node32, ruleToSkip pegRule) *node32 {
	for ; node != nil && node.pegRule == ruleToSkip; node = node.next {
	}
	return node
}

func skipWS(node *node32) *node32 {
	return skipNodes(node, ruleWS)
}

func assertNodeType(node *node32, expected pegRule) {
	if rule := node.pegRule; rule != expected {
		panic(fmt.Sprintf("node was %q, but wanted %q", rul3s[rule], rul3s[expected]))
	}
}

type wrapperFunc func(func())

type wrapperStack []wrapperFunc

func (w *wrapperStack) do(baseCase func()) {
	if len(*w) == 0 {
		baseCase()
		return
	}

	wrapper := (*w)[0]
	*w = (*w)[1:]
	wrapper(func() { w.do(baseCase) })
}

// localTargetName returns the name of the local target label for a global
// symbol named name.
func localTargetName(name string) string {
	return ".L" + name + "_local_target"
}

func localEntryName(name string) string {
	return ".L" + name + "_local_entry"
}

func isSynthesized(symbol string) bool {
	return strings.HasSuffix(symbol, "_bss_get") ||
		symbol == "OPENSSL_ia32cap_get" ||
		strings.HasPrefix(symbol, "BORINGSSL_bcm_text_")
}

func redirectorName(symbol string) string {
	return "bcm_redirector_" + symbol
}

// sectionType returns the type of a section. I.e. a section called “.text.foo”
// is a “.text” section.
func sectionType(section string) (string, bool) {
	if len(section) == 0 || section[0] != '.' {
		return "", false
	}

	i := strings.Index(section[1:], ".")
	if i != -1 {
		section = section[:i+1]
	}

	if strings.HasPrefix(section, ".debug_") {
		return ".debug", true
	}

	return section, true
}

// accessorName returns the name of the accessor function for a BSS symbol
// named name.
func accessorName(name string) string {
	return name + "_bss_get"
}

func (d *delocation) mapLocalSymbol(symbol string) string {
	if d.currentInput.index == 0 {
		return symbol
	}
	return symbol + "_BCM_" + strconv.Itoa(d.currentInput.index)
}

func detectProcessor(input inputFile) processorType {
	for statement := input.ast.up; statement != nil; statement = statement.next {
		node := skipNodes(statement.up, ruleWS)
		if node == nil || node.pegRule != ruleInstruction {
			continue
		}

		instruction := node.up
		instructionName := input.contents[instruction.begin:instruction.end]

		switch instructionName {
		case "movq", "call", "leaq":
			return x86_64
		case "addis", "addi", "mflr":
			return ppc64le
		}
	}

	panic("processed entire input and didn't recognise any instructions.")
}

func sortedSet(m map[string]struct{}) []string {
	ret := make([]string, 0, len(m))
	for key := range m {
		ret = append(ret, key)
	}
	sort.Strings(ret)
	return ret
}
