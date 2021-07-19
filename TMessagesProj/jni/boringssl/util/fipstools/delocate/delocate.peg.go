package main

//go:generate peg delocate.peg

import (
	"fmt"
	"math"
	"sort"
	"strconv"
)

const endSymbol rune = 1114112

/* The rule types inferred from the grammar are below. */
type pegRule uint8

const (
	ruleUnknown pegRule = iota
	ruleAsmFile
	ruleStatement
	ruleGlobalDirective
	ruleDirective
	ruleDirectiveName
	ruleLocationDirective
	ruleArgs
	ruleArg
	ruleQuotedArg
	ruleQuotedText
	ruleLabelContainingDirective
	ruleLabelContainingDirectiveName
	ruleSymbolArgs
	ruleSymbolArg
	ruleSymbolType
	ruleDot
	ruleTCMarker
	ruleEscapedChar
	ruleWS
	ruleComment
	ruleLabel
	ruleSymbolName
	ruleLocalSymbol
	ruleLocalLabel
	ruleLocalLabelRef
	ruleInstruction
	ruleInstructionName
	ruleInstructionArg
	ruleAVX512Token
	ruleTOCRefHigh
	ruleTOCRefLow
	ruleIndirectionIndicator
	ruleRegisterOrConstant
	ruleMemoryRef
	ruleSymbolRef
	ruleBaseIndexScale
	ruleOperator
	ruleOffset
	ruleSection
	ruleSegmentRegister
)

var rul3s = [...]string{
	"Unknown",
	"AsmFile",
	"Statement",
	"GlobalDirective",
	"Directive",
	"DirectiveName",
	"LocationDirective",
	"Args",
	"Arg",
	"QuotedArg",
	"QuotedText",
	"LabelContainingDirective",
	"LabelContainingDirectiveName",
	"SymbolArgs",
	"SymbolArg",
	"SymbolType",
	"Dot",
	"TCMarker",
	"EscapedChar",
	"WS",
	"Comment",
	"Label",
	"SymbolName",
	"LocalSymbol",
	"LocalLabel",
	"LocalLabelRef",
	"Instruction",
	"InstructionName",
	"InstructionArg",
	"AVX512Token",
	"TOCRefHigh",
	"TOCRefLow",
	"IndirectionIndicator",
	"RegisterOrConstant",
	"MemoryRef",
	"SymbolRef",
	"BaseIndexScale",
	"Operator",
	"Offset",
	"Section",
	"SegmentRegister",
}

type token32 struct {
	pegRule
	begin, end uint32
}

func (t *token32) String() string {
	return fmt.Sprintf("\x1B[34m%v\x1B[m %v %v", rul3s[t.pegRule], t.begin, t.end)
}

type node32 struct {
	token32
	up, next *node32
}

func (node *node32) print(pretty bool, buffer string) {
	var print func(node *node32, depth int)
	print = func(node *node32, depth int) {
		for node != nil {
			for c := 0; c < depth; c++ {
				fmt.Printf(" ")
			}
			rule := rul3s[node.pegRule]
			quote := strconv.Quote(string(([]rune(buffer)[node.begin:node.end])))
			if !pretty {
				fmt.Printf("%v %v\n", rule, quote)
			} else {
				fmt.Printf("\x1B[34m%v\x1B[m %v\n", rule, quote)
			}
			if node.up != nil {
				print(node.up, depth+1)
			}
			node = node.next
		}
	}
	print(node, 0)
}

func (node *node32) Print(buffer string) {
	node.print(false, buffer)
}

func (node *node32) PrettyPrint(buffer string) {
	node.print(true, buffer)
}

type tokens32 struct {
	tree []token32
}

func (t *tokens32) Trim(length uint32) {
	t.tree = t.tree[:length]
}

func (t *tokens32) Print() {
	for _, token := range t.tree {
		fmt.Println(token.String())
	}
}

func (t *tokens32) AST() *node32 {
	type element struct {
		node *node32
		down *element
	}
	tokens := t.Tokens()
	var stack *element
	for _, token := range tokens {
		if token.begin == token.end {
			continue
		}
		node := &node32{token32: token}
		for stack != nil && stack.node.begin >= token.begin && stack.node.end <= token.end {
			stack.node.next = node.up
			node.up = stack.node
			stack = stack.down
		}
		stack = &element{node: node, down: stack}
	}
	if stack != nil {
		return stack.node
	}
	return nil
}

func (t *tokens32) PrintSyntaxTree(buffer string) {
	t.AST().Print(buffer)
}

func (t *tokens32) PrettyPrintSyntaxTree(buffer string) {
	t.AST().PrettyPrint(buffer)
}

func (t *tokens32) Add(rule pegRule, begin, end, index uint32) {
	if tree := t.tree; int(index) >= len(tree) {
		expanded := make([]token32, 2*len(tree))
		copy(expanded, tree)
		t.tree = expanded
	}
	t.tree[index] = token32{
		pegRule: rule,
		begin:   begin,
		end:     end,
	}
}

func (t *tokens32) Tokens() []token32 {
	return t.tree
}

type Asm struct {
	Buffer string
	buffer []rune
	rules  [41]func() bool
	parse  func(rule ...int) error
	reset  func()
	Pretty bool
	tokens32
}

func (p *Asm) Parse(rule ...int) error {
	return p.parse(rule...)
}

func (p *Asm) Reset() {
	p.reset()
}

type textPosition struct {
	line, symbol int
}

type textPositionMap map[int]textPosition

func translatePositions(buffer []rune, positions []int) textPositionMap {
	length, translations, j, line, symbol := len(positions), make(textPositionMap, len(positions)), 0, 1, 0
	sort.Ints(positions)

search:
	for i, c := range buffer {
		if c == '\n' {
			line, symbol = line+1, 0
		} else {
			symbol++
		}
		if i == positions[j] {
			translations[positions[j]] = textPosition{line, symbol}
			for j++; j < length; j++ {
				if i != positions[j] {
					continue search
				}
			}
			break search
		}
	}

	return translations
}

type parseError struct {
	p   *Asm
	max token32
}

func (e *parseError) Error() string {
	tokens, error := []token32{e.max}, "\n"
	positions, p := make([]int, 2*len(tokens)), 0
	for _, token := range tokens {
		positions[p], p = int(token.begin), p+1
		positions[p], p = int(token.end), p+1
	}
	translations := translatePositions(e.p.buffer, positions)
	format := "parse error near %v (line %v symbol %v - line %v symbol %v):\n%v\n"
	if e.p.Pretty {
		format = "parse error near \x1B[34m%v\x1B[m (line %v symbol %v - line %v symbol %v):\n%v\n"
	}
	for _, token := range tokens {
		begin, end := int(token.begin), int(token.end)
		error += fmt.Sprintf(format,
			rul3s[token.pegRule],
			translations[begin].line, translations[begin].symbol,
			translations[end].line, translations[end].symbol,
			strconv.Quote(string(e.p.buffer[begin:end])))
	}

	return error
}

func (p *Asm) PrintSyntaxTree() {
	if p.Pretty {
		p.tokens32.PrettyPrintSyntaxTree(p.Buffer)
	} else {
		p.tokens32.PrintSyntaxTree(p.Buffer)
	}
}

func (p *Asm) Init() {
	var (
		max                  token32
		position, tokenIndex uint32
		buffer               []rune
	)
	p.reset = func() {
		max = token32{}
		position, tokenIndex = 0, 0

		p.buffer = []rune(p.Buffer)
		if len(p.buffer) == 0 || p.buffer[len(p.buffer)-1] != endSymbol {
			p.buffer = append(p.buffer, endSymbol)
		}
		buffer = p.buffer
	}
	p.reset()

	_rules := p.rules
	tree := tokens32{tree: make([]token32, math.MaxInt16)}
	p.parse = func(rule ...int) error {
		r := 1
		if len(rule) > 0 {
			r = rule[0]
		}
		matches := p.rules[r]()
		p.tokens32 = tree
		if matches {
			p.Trim(tokenIndex)
			return nil
		}
		return &parseError{p, max}
	}

	add := func(rule pegRule, begin uint32) {
		tree.Add(rule, begin, position, tokenIndex)
		tokenIndex++
		if begin != position && position > max.end {
			max = token32{rule, begin, position}
		}
	}

	matchDot := func() bool {
		if buffer[position] != endSymbol {
			position++
			return true
		}
		return false
	}

	/*matchChar := func(c byte) bool {
		if buffer[position] == c {
			position++
			return true
		}
		return false
	}*/

	/*matchRange := func(lower byte, upper byte) bool {
		if c := buffer[position]; c >= lower && c <= upper {
			position++
			return true
		}
		return false
	}*/

	_rules = [...]func() bool{
		nil,
		/* 0 AsmFile <- <(Statement* !.)> */
		func() bool {
			position0, tokenIndex0 := position, tokenIndex
			{
				position1 := position
			l2:
				{
					position3, tokenIndex3 := position, tokenIndex
					if !_rules[ruleStatement]() {
						goto l3
					}
					goto l2
				l3:
					position, tokenIndex = position3, tokenIndex3
				}
				{
					position4, tokenIndex4 := position, tokenIndex
					if !matchDot() {
						goto l4
					}
					goto l0
				l4:
					position, tokenIndex = position4, tokenIndex4
				}
				add(ruleAsmFile, position1)
			}
			return true
		l0:
			position, tokenIndex = position0, tokenIndex0
			return false
		},
		/* 1 Statement <- <(WS? (Label / ((GlobalDirective / LocationDirective / LabelContainingDirective / Instruction / Directive / Comment / ) WS? ((Comment? '\n') / ';'))))> */
		func() bool {
			position5, tokenIndex5 := position, tokenIndex
			{
				position6 := position
				{
					position7, tokenIndex7 := position, tokenIndex
					if !_rules[ruleWS]() {
						goto l7
					}
					goto l8
				l7:
					position, tokenIndex = position7, tokenIndex7
				}
			l8:
				{
					position9, tokenIndex9 := position, tokenIndex
					if !_rules[ruleLabel]() {
						goto l10
					}
					goto l9
				l10:
					position, tokenIndex = position9, tokenIndex9
					{
						position11, tokenIndex11 := position, tokenIndex
						if !_rules[ruleGlobalDirective]() {
							goto l12
						}
						goto l11
					l12:
						position, tokenIndex = position11, tokenIndex11
						if !_rules[ruleLocationDirective]() {
							goto l13
						}
						goto l11
					l13:
						position, tokenIndex = position11, tokenIndex11
						if !_rules[ruleLabelContainingDirective]() {
							goto l14
						}
						goto l11
					l14:
						position, tokenIndex = position11, tokenIndex11
						if !_rules[ruleInstruction]() {
							goto l15
						}
						goto l11
					l15:
						position, tokenIndex = position11, tokenIndex11
						if !_rules[ruleDirective]() {
							goto l16
						}
						goto l11
					l16:
						position, tokenIndex = position11, tokenIndex11
						if !_rules[ruleComment]() {
							goto l17
						}
						goto l11
					l17:
						position, tokenIndex = position11, tokenIndex11
					}
				l11:
					{
						position18, tokenIndex18 := position, tokenIndex
						if !_rules[ruleWS]() {
							goto l18
						}
						goto l19
					l18:
						position, tokenIndex = position18, tokenIndex18
					}
				l19:
					{
						position20, tokenIndex20 := position, tokenIndex
						{
							position22, tokenIndex22 := position, tokenIndex
							if !_rules[ruleComment]() {
								goto l22
							}
							goto l23
						l22:
							position, tokenIndex = position22, tokenIndex22
						}
					l23:
						if buffer[position] != rune('\n') {
							goto l21
						}
						position++
						goto l20
					l21:
						position, tokenIndex = position20, tokenIndex20
						if buffer[position] != rune(';') {
							goto l5
						}
						position++
					}
				l20:
				}
			l9:
				add(ruleStatement, position6)
			}
			return true
		l5:
			position, tokenIndex = position5, tokenIndex5
			return false
		},
		/* 2 GlobalDirective <- <((('.' ('g' / 'G') ('l' / 'L') ('o' / 'O') ('b' / 'B') ('a' / 'A') ('l' / 'L')) / ('.' ('g' / 'G') ('l' / 'L') ('o' / 'O') ('b' / 'B') ('l' / 'L'))) WS SymbolName)> */
		func() bool {
			position24, tokenIndex24 := position, tokenIndex
			{
				position25 := position
				{
					position26, tokenIndex26 := position, tokenIndex
					if buffer[position] != rune('.') {
						goto l27
					}
					position++
					{
						position28, tokenIndex28 := position, tokenIndex
						if buffer[position] != rune('g') {
							goto l29
						}
						position++
						goto l28
					l29:
						position, tokenIndex = position28, tokenIndex28
						if buffer[position] != rune('G') {
							goto l27
						}
						position++
					}
				l28:
					{
						position30, tokenIndex30 := position, tokenIndex
						if buffer[position] != rune('l') {
							goto l31
						}
						position++
						goto l30
					l31:
						position, tokenIndex = position30, tokenIndex30
						if buffer[position] != rune('L') {
							goto l27
						}
						position++
					}
				l30:
					{
						position32, tokenIndex32 := position, tokenIndex
						if buffer[position] != rune('o') {
							goto l33
						}
						position++
						goto l32
					l33:
						position, tokenIndex = position32, tokenIndex32
						if buffer[position] != rune('O') {
							goto l27
						}
						position++
					}
				l32:
					{
						position34, tokenIndex34 := position, tokenIndex
						if buffer[position] != rune('b') {
							goto l35
						}
						position++
						goto l34
					l35:
						position, tokenIndex = position34, tokenIndex34
						if buffer[position] != rune('B') {
							goto l27
						}
						position++
					}
				l34:
					{
						position36, tokenIndex36 := position, tokenIndex
						if buffer[position] != rune('a') {
							goto l37
						}
						position++
						goto l36
					l37:
						position, tokenIndex = position36, tokenIndex36
						if buffer[position] != rune('A') {
							goto l27
						}
						position++
					}
				l36:
					{
						position38, tokenIndex38 := position, tokenIndex
						if buffer[position] != rune('l') {
							goto l39
						}
						position++
						goto l38
					l39:
						position, tokenIndex = position38, tokenIndex38
						if buffer[position] != rune('L') {
							goto l27
						}
						position++
					}
				l38:
					goto l26
				l27:
					position, tokenIndex = position26, tokenIndex26
					if buffer[position] != rune('.') {
						goto l24
					}
					position++
					{
						position40, tokenIndex40 := position, tokenIndex
						if buffer[position] != rune('g') {
							goto l41
						}
						position++
						goto l40
					l41:
						position, tokenIndex = position40, tokenIndex40
						if buffer[position] != rune('G') {
							goto l24
						}
						position++
					}
				l40:
					{
						position42, tokenIndex42 := position, tokenIndex
						if buffer[position] != rune('l') {
							goto l43
						}
						position++
						goto l42
					l43:
						position, tokenIndex = position42, tokenIndex42
						if buffer[position] != rune('L') {
							goto l24
						}
						position++
					}
				l42:
					{
						position44, tokenIndex44 := position, tokenIndex
						if buffer[position] != rune('o') {
							goto l45
						}
						position++
						goto l44
					l45:
						position, tokenIndex = position44, tokenIndex44
						if buffer[position] != rune('O') {
							goto l24
						}
						position++
					}
				l44:
					{
						position46, tokenIndex46 := position, tokenIndex
						if buffer[position] != rune('b') {
							goto l47
						}
						position++
						goto l46
					l47:
						position, tokenIndex = position46, tokenIndex46
						if buffer[position] != rune('B') {
							goto l24
						}
						position++
					}
				l46:
					{
						position48, tokenIndex48 := position, tokenIndex
						if buffer[position] != rune('l') {
							goto l49
						}
						position++
						goto l48
					l49:
						position, tokenIndex = position48, tokenIndex48
						if buffer[position] != rune('L') {
							goto l24
						}
						position++
					}
				l48:
				}
			l26:
				if !_rules[ruleWS]() {
					goto l24
				}
				if !_rules[ruleSymbolName]() {
					goto l24
				}
				add(ruleGlobalDirective, position25)
			}
			return true
		l24:
			position, tokenIndex = position24, tokenIndex24
			return false
		},
		/* 3 Directive <- <('.' DirectiveName (WS Args)?)> */
		func() bool {
			position50, tokenIndex50 := position, tokenIndex
			{
				position51 := position
				if buffer[position] != rune('.') {
					goto l50
				}
				position++
				if !_rules[ruleDirectiveName]() {
					goto l50
				}
				{
					position52, tokenIndex52 := position, tokenIndex
					if !_rules[ruleWS]() {
						goto l52
					}
					if !_rules[ruleArgs]() {
						goto l52
					}
					goto l53
				l52:
					position, tokenIndex = position52, tokenIndex52
				}
			l53:
				add(ruleDirective, position51)
			}
			return true
		l50:
			position, tokenIndex = position50, tokenIndex50
			return false
		},
		/* 4 DirectiveName <- <([a-z] / [A-Z] / ([0-9] / [0-9]) / '_')+> */
		func() bool {
			position54, tokenIndex54 := position, tokenIndex
			{
				position55 := position
				{
					position58, tokenIndex58 := position, tokenIndex
					if c := buffer[position]; c < rune('a') || c > rune('z') {
						goto l59
					}
					position++
					goto l58
				l59:
					position, tokenIndex = position58, tokenIndex58
					if c := buffer[position]; c < rune('A') || c > rune('Z') {
						goto l60
					}
					position++
					goto l58
				l60:
					position, tokenIndex = position58, tokenIndex58
					{
						position62, tokenIndex62 := position, tokenIndex
						if c := buffer[position]; c < rune('0') || c > rune('9') {
							goto l63
						}
						position++
						goto l62
					l63:
						position, tokenIndex = position62, tokenIndex62
						if c := buffer[position]; c < rune('0') || c > rune('9') {
							goto l61
						}
						position++
					}
				l62:
					goto l58
				l61:
					position, tokenIndex = position58, tokenIndex58
					if buffer[position] != rune('_') {
						goto l54
					}
					position++
				}
			l58:
			l56:
				{
					position57, tokenIndex57 := position, tokenIndex
					{
						position64, tokenIndex64 := position, tokenIndex
						if c := buffer[position]; c < rune('a') || c > rune('z') {
							goto l65
						}
						position++
						goto l64
					l65:
						position, tokenIndex = position64, tokenIndex64
						if c := buffer[position]; c < rune('A') || c > rune('Z') {
							goto l66
						}
						position++
						goto l64
					l66:
						position, tokenIndex = position64, tokenIndex64
						{
							position68, tokenIndex68 := position, tokenIndex
							if c := buffer[position]; c < rune('0') || c > rune('9') {
								goto l69
							}
							position++
							goto l68
						l69:
							position, tokenIndex = position68, tokenIndex68
							if c := buffer[position]; c < rune('0') || c > rune('9') {
								goto l67
							}
							position++
						}
					l68:
						goto l64
					l67:
						position, tokenIndex = position64, tokenIndex64
						if buffer[position] != rune('_') {
							goto l57
						}
						position++
					}
				l64:
					goto l56
				l57:
					position, tokenIndex = position57, tokenIndex57
				}
				add(ruleDirectiveName, position55)
			}
			return true
		l54:
			position, tokenIndex = position54, tokenIndex54
			return false
		},
		/* 5 LocationDirective <- <((('.' ('f' / 'F') ('i' / 'I') ('l' / 'L') ('e' / 'E')) / ('.' ('l' / 'L') ('o' / 'O') ('c' / 'C'))) WS (!('#' / '\n') .)+)> */
		func() bool {
			position70, tokenIndex70 := position, tokenIndex
			{
				position71 := position
				{
					position72, tokenIndex72 := position, tokenIndex
					if buffer[position] != rune('.') {
						goto l73
					}
					position++
					{
						position74, tokenIndex74 := position, tokenIndex
						if buffer[position] != rune('f') {
							goto l75
						}
						position++
						goto l74
					l75:
						position, tokenIndex = position74, tokenIndex74
						if buffer[position] != rune('F') {
							goto l73
						}
						position++
					}
				l74:
					{
						position76, tokenIndex76 := position, tokenIndex
						if buffer[position] != rune('i') {
							goto l77
						}
						position++
						goto l76
					l77:
						position, tokenIndex = position76, tokenIndex76
						if buffer[position] != rune('I') {
							goto l73
						}
						position++
					}
				l76:
					{
						position78, tokenIndex78 := position, tokenIndex
						if buffer[position] != rune('l') {
							goto l79
						}
						position++
						goto l78
					l79:
						position, tokenIndex = position78, tokenIndex78
						if buffer[position] != rune('L') {
							goto l73
						}
						position++
					}
				l78:
					{
						position80, tokenIndex80 := position, tokenIndex
						if buffer[position] != rune('e') {
							goto l81
						}
						position++
						goto l80
					l81:
						position, tokenIndex = position80, tokenIndex80
						if buffer[position] != rune('E') {
							goto l73
						}
						position++
					}
				l80:
					goto l72
				l73:
					position, tokenIndex = position72, tokenIndex72
					if buffer[position] != rune('.') {
						goto l70
					}
					position++
					{
						position82, tokenIndex82 := position, tokenIndex
						if buffer[position] != rune('l') {
							goto l83
						}
						position++
						goto l82
					l83:
						position, tokenIndex = position82, tokenIndex82
						if buffer[position] != rune('L') {
							goto l70
						}
						position++
					}
				l82:
					{
						position84, tokenIndex84 := position, tokenIndex
						if buffer[position] != rune('o') {
							goto l85
						}
						position++
						goto l84
					l85:
						position, tokenIndex = position84, tokenIndex84
						if buffer[position] != rune('O') {
							goto l70
						}
						position++
					}
				l84:
					{
						position86, tokenIndex86 := position, tokenIndex
						if buffer[position] != rune('c') {
							goto l87
						}
						position++
						goto l86
					l87:
						position, tokenIndex = position86, tokenIndex86
						if buffer[position] != rune('C') {
							goto l70
						}
						position++
					}
				l86:
				}
			l72:
				if !_rules[ruleWS]() {
					goto l70
				}
				{
					position90, tokenIndex90 := position, tokenIndex
					{
						position91, tokenIndex91 := position, tokenIndex
						if buffer[position] != rune('#') {
							goto l92
						}
						position++
						goto l91
					l92:
						position, tokenIndex = position91, tokenIndex91
						if buffer[position] != rune('\n') {
							goto l90
						}
						position++
					}
				l91:
					goto l70
				l90:
					position, tokenIndex = position90, tokenIndex90
				}
				if !matchDot() {
					goto l70
				}
			l88:
				{
					position89, tokenIndex89 := position, tokenIndex
					{
						position93, tokenIndex93 := position, tokenIndex
						{
							position94, tokenIndex94 := position, tokenIndex
							if buffer[position] != rune('#') {
								goto l95
							}
							position++
							goto l94
						l95:
							position, tokenIndex = position94, tokenIndex94
							if buffer[position] != rune('\n') {
								goto l93
							}
							position++
						}
					l94:
						goto l89
					l93:
						position, tokenIndex = position93, tokenIndex93
					}
					if !matchDot() {
						goto l89
					}
					goto l88
				l89:
					position, tokenIndex = position89, tokenIndex89
				}
				add(ruleLocationDirective, position71)
			}
			return true
		l70:
			position, tokenIndex = position70, tokenIndex70
			return false
		},
		/* 6 Args <- <(Arg (WS? ',' WS? Arg)*)> */
		func() bool {
			position96, tokenIndex96 := position, tokenIndex
			{
				position97 := position
				if !_rules[ruleArg]() {
					goto l96
				}
			l98:
				{
					position99, tokenIndex99 := position, tokenIndex
					{
						position100, tokenIndex100 := position, tokenIndex
						if !_rules[ruleWS]() {
							goto l100
						}
						goto l101
					l100:
						position, tokenIndex = position100, tokenIndex100
					}
				l101:
					if buffer[position] != rune(',') {
						goto l99
					}
					position++
					{
						position102, tokenIndex102 := position, tokenIndex
						if !_rules[ruleWS]() {
							goto l102
						}
						goto l103
					l102:
						position, tokenIndex = position102, tokenIndex102
					}
				l103:
					if !_rules[ruleArg]() {
						goto l99
					}
					goto l98
				l99:
					position, tokenIndex = position99, tokenIndex99
				}
				add(ruleArgs, position97)
			}
			return true
		l96:
			position, tokenIndex = position96, tokenIndex96
			return false
		},
		/* 7 Arg <- <(QuotedArg / ([0-9] / [0-9] / ([a-z] / [A-Z]) / '%' / '+' / '-' / '*' / '_' / '@' / '.')*)> */
		func() bool {
			{
				position105 := position
				{
					position106, tokenIndex106 := position, tokenIndex
					if !_rules[ruleQuotedArg]() {
						goto l107
					}
					goto l106
				l107:
					position, tokenIndex = position106, tokenIndex106
				l108:
					{
						position109, tokenIndex109 := position, tokenIndex
						{
							position110, tokenIndex110 := position, tokenIndex
							if c := buffer[position]; c < rune('0') || c > rune('9') {
								goto l111
							}
							position++
							goto l110
						l111:
							position, tokenIndex = position110, tokenIndex110
							if c := buffer[position]; c < rune('0') || c > rune('9') {
								goto l112
							}
							position++
							goto l110
						l112:
							position, tokenIndex = position110, tokenIndex110
							{
								position114, tokenIndex114 := position, tokenIndex
								if c := buffer[position]; c < rune('a') || c > rune('z') {
									goto l115
								}
								position++
								goto l114
							l115:
								position, tokenIndex = position114, tokenIndex114
								if c := buffer[position]; c < rune('A') || c > rune('Z') {
									goto l113
								}
								position++
							}
						l114:
							goto l110
						l113:
							position, tokenIndex = position110, tokenIndex110
							if buffer[position] != rune('%') {
								goto l116
							}
							position++
							goto l110
						l116:
							position, tokenIndex = position110, tokenIndex110
							if buffer[position] != rune('+') {
								goto l117
							}
							position++
							goto l110
						l117:
							position, tokenIndex = position110, tokenIndex110
							if buffer[position] != rune('-') {
								goto l118
							}
							position++
							goto l110
						l118:
							position, tokenIndex = position110, tokenIndex110
							if buffer[position] != rune('*') {
								goto l119
							}
							position++
							goto l110
						l119:
							position, tokenIndex = position110, tokenIndex110
							if buffer[position] != rune('_') {
								goto l120
							}
							position++
							goto l110
						l120:
							position, tokenIndex = position110, tokenIndex110
							if buffer[position] != rune('@') {
								goto l121
							}
							position++
							goto l110
						l121:
							position, tokenIndex = position110, tokenIndex110
							if buffer[position] != rune('.') {
								goto l109
							}
							position++
						}
					l110:
						goto l108
					l109:
						position, tokenIndex = position109, tokenIndex109
					}
				}
			l106:
				add(ruleArg, position105)
			}
			return true
		},
		/* 8 QuotedArg <- <('"' QuotedText '"')> */
		func() bool {
			position122, tokenIndex122 := position, tokenIndex
			{
				position123 := position
				if buffer[position] != rune('"') {
					goto l122
				}
				position++
				if !_rules[ruleQuotedText]() {
					goto l122
				}
				if buffer[position] != rune('"') {
					goto l122
				}
				position++
				add(ruleQuotedArg, position123)
			}
			return true
		l122:
			position, tokenIndex = position122, tokenIndex122
			return false
		},
		/* 9 QuotedText <- <(EscapedChar / (!'"' .))*> */
		func() bool {
			{
				position125 := position
			l126:
				{
					position127, tokenIndex127 := position, tokenIndex
					{
						position128, tokenIndex128 := position, tokenIndex
						if !_rules[ruleEscapedChar]() {
							goto l129
						}
						goto l128
					l129:
						position, tokenIndex = position128, tokenIndex128
						{
							position130, tokenIndex130 := position, tokenIndex
							if buffer[position] != rune('"') {
								goto l130
							}
							position++
							goto l127
						l130:
							position, tokenIndex = position130, tokenIndex130
						}
						if !matchDot() {
							goto l127
						}
					}
				l128:
					goto l126
				l127:
					position, tokenIndex = position127, tokenIndex127
				}
				add(ruleQuotedText, position125)
			}
			return true
		},
		/* 10 LabelContainingDirective <- <(LabelContainingDirectiveName WS SymbolArgs)> */
		func() bool {
			position131, tokenIndex131 := position, tokenIndex
			{
				position132 := position
				if !_rules[ruleLabelContainingDirectiveName]() {
					goto l131
				}
				if !_rules[ruleWS]() {
					goto l131
				}
				if !_rules[ruleSymbolArgs]() {
					goto l131
				}
				add(ruleLabelContainingDirective, position132)
			}
			return true
		l131:
			position, tokenIndex = position131, tokenIndex131
			return false
		},
		/* 11 LabelContainingDirectiveName <- <(('.' ('l' / 'L') ('o' / 'O') ('n' / 'N') ('g' / 'G')) / ('.' ('s' / 'S') ('e' / 'E') ('t' / 'T')) / ('.' '8' ('b' / 'B') ('y' / 'Y') ('t' / 'T') ('e' / 'E')) / ('.' '4' ('b' / 'B') ('y' / 'Y') ('t' / 'T') ('e' / 'E')) / ('.' ('q' / 'Q') ('u' / 'U') ('a' / 'A') ('d' / 'D')) / ('.' ('t' / 'T') ('c' / 'C')) / ('.' ('l' / 'L') ('o' / 'O') ('c' / 'C') ('a' / 'A') ('l' / 'L') ('e' / 'E') ('n' / 'N') ('t' / 'T') ('r' / 'R') ('y' / 'Y')) / ('.' ('s' / 'S') ('i' / 'I') ('z' / 'Z') ('e' / 'E')) / ('.' ('t' / 'T') ('y' / 'Y') ('p' / 'P') ('e' / 'E')) / ('.' ('u' / 'U') ('l' / 'L') ('e' / 'E') ('b' / 'B') '1' '2' '8') / ('.' ('s' / 'S') ('l' / 'L') ('e' / 'E') ('b' / 'B') '1' '2' '8'))> */
		func() bool {
			position133, tokenIndex133 := position, tokenIndex
			{
				position134 := position
				{
					position135, tokenIndex135 := position, tokenIndex
					if buffer[position] != rune('.') {
						goto l136
					}
					position++
					{
						position137, tokenIndex137 := position, tokenIndex
						if buffer[position] != rune('l') {
							goto l138
						}
						position++
						goto l137
					l138:
						position, tokenIndex = position137, tokenIndex137
						if buffer[position] != rune('L') {
							goto l136
						}
						position++
					}
				l137:
					{
						position139, tokenIndex139 := position, tokenIndex
						if buffer[position] != rune('o') {
							goto l140
						}
						position++
						goto l139
					l140:
						position, tokenIndex = position139, tokenIndex139
						if buffer[position] != rune('O') {
							goto l136
						}
						position++
					}
				l139:
					{
						position141, tokenIndex141 := position, tokenIndex
						if buffer[position] != rune('n') {
							goto l142
						}
						position++
						goto l141
					l142:
						position, tokenIndex = position141, tokenIndex141
						if buffer[position] != rune('N') {
							goto l136
						}
						position++
					}
				l141:
					{
						position143, tokenIndex143 := position, tokenIndex
						if buffer[position] != rune('g') {
							goto l144
						}
						position++
						goto l143
					l144:
						position, tokenIndex = position143, tokenIndex143
						if buffer[position] != rune('G') {
							goto l136
						}
						position++
					}
				l143:
					goto l135
				l136:
					position, tokenIndex = position135, tokenIndex135
					if buffer[position] != rune('.') {
						goto l145
					}
					position++
					{
						position146, tokenIndex146 := position, tokenIndex
						if buffer[position] != rune('s') {
							goto l147
						}
						position++
						goto l146
					l147:
						position, tokenIndex = position146, tokenIndex146
						if buffer[position] != rune('S') {
							goto l145
						}
						position++
					}
				l146:
					{
						position148, tokenIndex148 := position, tokenIndex
						if buffer[position] != rune('e') {
							goto l149
						}
						position++
						goto l148
					l149:
						position, tokenIndex = position148, tokenIndex148
						if buffer[position] != rune('E') {
							goto l145
						}
						position++
					}
				l148:
					{
						position150, tokenIndex150 := position, tokenIndex
						if buffer[position] != rune('t') {
							goto l151
						}
						position++
						goto l150
					l151:
						position, tokenIndex = position150, tokenIndex150
						if buffer[position] != rune('T') {
							goto l145
						}
						position++
					}
				l150:
					goto l135
				l145:
					position, tokenIndex = position135, tokenIndex135
					if buffer[position] != rune('.') {
						goto l152
					}
					position++
					if buffer[position] != rune('8') {
						goto l152
					}
					position++
					{
						position153, tokenIndex153 := position, tokenIndex
						if buffer[position] != rune('b') {
							goto l154
						}
						position++
						goto l153
					l154:
						position, tokenIndex = position153, tokenIndex153
						if buffer[position] != rune('B') {
							goto l152
						}
						position++
					}
				l153:
					{
						position155, tokenIndex155 := position, tokenIndex
						if buffer[position] != rune('y') {
							goto l156
						}
						position++
						goto l155
					l156:
						position, tokenIndex = position155, tokenIndex155
						if buffer[position] != rune('Y') {
							goto l152
						}
						position++
					}
				l155:
					{
						position157, tokenIndex157 := position, tokenIndex
						if buffer[position] != rune('t') {
							goto l158
						}
						position++
						goto l157
					l158:
						position, tokenIndex = position157, tokenIndex157
						if buffer[position] != rune('T') {
							goto l152
						}
						position++
					}
				l157:
					{
						position159, tokenIndex159 := position, tokenIndex
						if buffer[position] != rune('e') {
							goto l160
						}
						position++
						goto l159
					l160:
						position, tokenIndex = position159, tokenIndex159
						if buffer[position] != rune('E') {
							goto l152
						}
						position++
					}
				l159:
					goto l135
				l152:
					position, tokenIndex = position135, tokenIndex135
					if buffer[position] != rune('.') {
						goto l161
					}
					position++
					if buffer[position] != rune('4') {
						goto l161
					}
					position++
					{
						position162, tokenIndex162 := position, tokenIndex
						if buffer[position] != rune('b') {
							goto l163
						}
						position++
						goto l162
					l163:
						position, tokenIndex = position162, tokenIndex162
						if buffer[position] != rune('B') {
							goto l161
						}
						position++
					}
				l162:
					{
						position164, tokenIndex164 := position, tokenIndex
						if buffer[position] != rune('y') {
							goto l165
						}
						position++
						goto l164
					l165:
						position, tokenIndex = position164, tokenIndex164
						if buffer[position] != rune('Y') {
							goto l161
						}
						position++
					}
				l164:
					{
						position166, tokenIndex166 := position, tokenIndex
						if buffer[position] != rune('t') {
							goto l167
						}
						position++
						goto l166
					l167:
						position, tokenIndex = position166, tokenIndex166
						if buffer[position] != rune('T') {
							goto l161
						}
						position++
					}
				l166:
					{
						position168, tokenIndex168 := position, tokenIndex
						if buffer[position] != rune('e') {
							goto l169
						}
						position++
						goto l168
					l169:
						position, tokenIndex = position168, tokenIndex168
						if buffer[position] != rune('E') {
							goto l161
						}
						position++
					}
				l168:
					goto l135
				l161:
					position, tokenIndex = position135, tokenIndex135
					if buffer[position] != rune('.') {
						goto l170
					}
					position++
					{
						position171, tokenIndex171 := position, tokenIndex
						if buffer[position] != rune('q') {
							goto l172
						}
						position++
						goto l171
					l172:
						position, tokenIndex = position171, tokenIndex171
						if buffer[position] != rune('Q') {
							goto l170
						}
						position++
					}
				l171:
					{
						position173, tokenIndex173 := position, tokenIndex
						if buffer[position] != rune('u') {
							goto l174
						}
						position++
						goto l173
					l174:
						position, tokenIndex = position173, tokenIndex173
						if buffer[position] != rune('U') {
							goto l170
						}
						position++
					}
				l173:
					{
						position175, tokenIndex175 := position, tokenIndex
						if buffer[position] != rune('a') {
							goto l176
						}
						position++
						goto l175
					l176:
						position, tokenIndex = position175, tokenIndex175
						if buffer[position] != rune('A') {
							goto l170
						}
						position++
					}
				l175:
					{
						position177, tokenIndex177 := position, tokenIndex
						if buffer[position] != rune('d') {
							goto l178
						}
						position++
						goto l177
					l178:
						position, tokenIndex = position177, tokenIndex177
						if buffer[position] != rune('D') {
							goto l170
						}
						position++
					}
				l177:
					goto l135
				l170:
					position, tokenIndex = position135, tokenIndex135
					if buffer[position] != rune('.') {
						goto l179
					}
					position++
					{
						position180, tokenIndex180 := position, tokenIndex
						if buffer[position] != rune('t') {
							goto l181
						}
						position++
						goto l180
					l181:
						position, tokenIndex = position180, tokenIndex180
						if buffer[position] != rune('T') {
							goto l179
						}
						position++
					}
				l180:
					{
						position182, tokenIndex182 := position, tokenIndex
						if buffer[position] != rune('c') {
							goto l183
						}
						position++
						goto l182
					l183:
						position, tokenIndex = position182, tokenIndex182
						if buffer[position] != rune('C') {
							goto l179
						}
						position++
					}
				l182:
					goto l135
				l179:
					position, tokenIndex = position135, tokenIndex135
					if buffer[position] != rune('.') {
						goto l184
					}
					position++
					{
						position185, tokenIndex185 := position, tokenIndex
						if buffer[position] != rune('l') {
							goto l186
						}
						position++
						goto l185
					l186:
						position, tokenIndex = position185, tokenIndex185
						if buffer[position] != rune('L') {
							goto l184
						}
						position++
					}
				l185:
					{
						position187, tokenIndex187 := position, tokenIndex
						if buffer[position] != rune('o') {
							goto l188
						}
						position++
						goto l187
					l188:
						position, tokenIndex = position187, tokenIndex187
						if buffer[position] != rune('O') {
							goto l184
						}
						position++
					}
				l187:
					{
						position189, tokenIndex189 := position, tokenIndex
						if buffer[position] != rune('c') {
							goto l190
						}
						position++
						goto l189
					l190:
						position, tokenIndex = position189, tokenIndex189
						if buffer[position] != rune('C') {
							goto l184
						}
						position++
					}
				l189:
					{
						position191, tokenIndex191 := position, tokenIndex
						if buffer[position] != rune('a') {
							goto l192
						}
						position++
						goto l191
					l192:
						position, tokenIndex = position191, tokenIndex191
						if buffer[position] != rune('A') {
							goto l184
						}
						position++
					}
				l191:
					{
						position193, tokenIndex193 := position, tokenIndex
						if buffer[position] != rune('l') {
							goto l194
						}
						position++
						goto l193
					l194:
						position, tokenIndex = position193, tokenIndex193
						if buffer[position] != rune('L') {
							goto l184
						}
						position++
					}
				l193:
					{
						position195, tokenIndex195 := position, tokenIndex
						if buffer[position] != rune('e') {
							goto l196
						}
						position++
						goto l195
					l196:
						position, tokenIndex = position195, tokenIndex195
						if buffer[position] != rune('E') {
							goto l184
						}
						position++
					}
				l195:
					{
						position197, tokenIndex197 := position, tokenIndex
						if buffer[position] != rune('n') {
							goto l198
						}
						position++
						goto l197
					l198:
						position, tokenIndex = position197, tokenIndex197
						if buffer[position] != rune('N') {
							goto l184
						}
						position++
					}
				l197:
					{
						position199, tokenIndex199 := position, tokenIndex
						if buffer[position] != rune('t') {
							goto l200
						}
						position++
						goto l199
					l200:
						position, tokenIndex = position199, tokenIndex199
						if buffer[position] != rune('T') {
							goto l184
						}
						position++
					}
				l199:
					{
						position201, tokenIndex201 := position, tokenIndex
						if buffer[position] != rune('r') {
							goto l202
						}
						position++
						goto l201
					l202:
						position, tokenIndex = position201, tokenIndex201
						if buffer[position] != rune('R') {
							goto l184
						}
						position++
					}
				l201:
					{
						position203, tokenIndex203 := position, tokenIndex
						if buffer[position] != rune('y') {
							goto l204
						}
						position++
						goto l203
					l204:
						position, tokenIndex = position203, tokenIndex203
						if buffer[position] != rune('Y') {
							goto l184
						}
						position++
					}
				l203:
					goto l135
				l184:
					position, tokenIndex = position135, tokenIndex135
					if buffer[position] != rune('.') {
						goto l205
					}
					position++
					{
						position206, tokenIndex206 := position, tokenIndex
						if buffer[position] != rune('s') {
							goto l207
						}
						position++
						goto l206
					l207:
						position, tokenIndex = position206, tokenIndex206
						if buffer[position] != rune('S') {
							goto l205
						}
						position++
					}
				l206:
					{
						position208, tokenIndex208 := position, tokenIndex
						if buffer[position] != rune('i') {
							goto l209
						}
						position++
						goto l208
					l209:
						position, tokenIndex = position208, tokenIndex208
						if buffer[position] != rune('I') {
							goto l205
						}
						position++
					}
				l208:
					{
						position210, tokenIndex210 := position, tokenIndex
						if buffer[position] != rune('z') {
							goto l211
						}
						position++
						goto l210
					l211:
						position, tokenIndex = position210, tokenIndex210
						if buffer[position] != rune('Z') {
							goto l205
						}
						position++
					}
				l210:
					{
						position212, tokenIndex212 := position, tokenIndex
						if buffer[position] != rune('e') {
							goto l213
						}
						position++
						goto l212
					l213:
						position, tokenIndex = position212, tokenIndex212
						if buffer[position] != rune('E') {
							goto l205
						}
						position++
					}
				l212:
					goto l135
				l205:
					position, tokenIndex = position135, tokenIndex135
					if buffer[position] != rune('.') {
						goto l214
					}
					position++
					{
						position215, tokenIndex215 := position, tokenIndex
						if buffer[position] != rune('t') {
							goto l216
						}
						position++
						goto l215
					l216:
						position, tokenIndex = position215, tokenIndex215
						if buffer[position] != rune('T') {
							goto l214
						}
						position++
					}
				l215:
					{
						position217, tokenIndex217 := position, tokenIndex
						if buffer[position] != rune('y') {
							goto l218
						}
						position++
						goto l217
					l218:
						position, tokenIndex = position217, tokenIndex217
						if buffer[position] != rune('Y') {
							goto l214
						}
						position++
					}
				l217:
					{
						position219, tokenIndex219 := position, tokenIndex
						if buffer[position] != rune('p') {
							goto l220
						}
						position++
						goto l219
					l220:
						position, tokenIndex = position219, tokenIndex219
						if buffer[position] != rune('P') {
							goto l214
						}
						position++
					}
				l219:
					{
						position221, tokenIndex221 := position, tokenIndex
						if buffer[position] != rune('e') {
							goto l222
						}
						position++
						goto l221
					l222:
						position, tokenIndex = position221, tokenIndex221
						if buffer[position] != rune('E') {
							goto l214
						}
						position++
					}
				l221:
					goto l135
				l214:
					position, tokenIndex = position135, tokenIndex135
					if buffer[position] != rune('.') {
						goto l223
					}
					position++
					{
						position224, tokenIndex224 := position, tokenIndex
						if buffer[position] != rune('u') {
							goto l225
						}
						position++
						goto l224
					l225:
						position, tokenIndex = position224, tokenIndex224
						if buffer[position] != rune('U') {
							goto l223
						}
						position++
					}
				l224:
					{
						position226, tokenIndex226 := position, tokenIndex
						if buffer[position] != rune('l') {
							goto l227
						}
						position++
						goto l226
					l227:
						position, tokenIndex = position226, tokenIndex226
						if buffer[position] != rune('L') {
							goto l223
						}
						position++
					}
				l226:
					{
						position228, tokenIndex228 := position, tokenIndex
						if buffer[position] != rune('e') {
							goto l229
						}
						position++
						goto l228
					l229:
						position, tokenIndex = position228, tokenIndex228
						if buffer[position] != rune('E') {
							goto l223
						}
						position++
					}
				l228:
					{
						position230, tokenIndex230 := position, tokenIndex
						if buffer[position] != rune('b') {
							goto l231
						}
						position++
						goto l230
					l231:
						position, tokenIndex = position230, tokenIndex230
						if buffer[position] != rune('B') {
							goto l223
						}
						position++
					}
				l230:
					if buffer[position] != rune('1') {
						goto l223
					}
					position++
					if buffer[position] != rune('2') {
						goto l223
					}
					position++
					if buffer[position] != rune('8') {
						goto l223
					}
					position++
					goto l135
				l223:
					position, tokenIndex = position135, tokenIndex135
					if buffer[position] != rune('.') {
						goto l133
					}
					position++
					{
						position232, tokenIndex232 := position, tokenIndex
						if buffer[position] != rune('s') {
							goto l233
						}
						position++
						goto l232
					l233:
						position, tokenIndex = position232, tokenIndex232
						if buffer[position] != rune('S') {
							goto l133
						}
						position++
					}
				l232:
					{
						position234, tokenIndex234 := position, tokenIndex
						if buffer[position] != rune('l') {
							goto l235
						}
						position++
						goto l234
					l235:
						position, tokenIndex = position234, tokenIndex234
						if buffer[position] != rune('L') {
							goto l133
						}
						position++
					}
				l234:
					{
						position236, tokenIndex236 := position, tokenIndex
						if buffer[position] != rune('e') {
							goto l237
						}
						position++
						goto l236
					l237:
						position, tokenIndex = position236, tokenIndex236
						if buffer[position] != rune('E') {
							goto l133
						}
						position++
					}
				l236:
					{
						position238, tokenIndex238 := position, tokenIndex
						if buffer[position] != rune('b') {
							goto l239
						}
						position++
						goto l238
					l239:
						position, tokenIndex = position238, tokenIndex238
						if buffer[position] != rune('B') {
							goto l133
						}
						position++
					}
				l238:
					if buffer[position] != rune('1') {
						goto l133
					}
					position++
					if buffer[position] != rune('2') {
						goto l133
					}
					position++
					if buffer[position] != rune('8') {
						goto l133
					}
					position++
				}
			l135:
				add(ruleLabelContainingDirectiveName, position134)
			}
			return true
		l133:
			position, tokenIndex = position133, tokenIndex133
			return false
		},
		/* 12 SymbolArgs <- <(SymbolArg (WS? ',' WS? SymbolArg)*)> */
		func() bool {
			position240, tokenIndex240 := position, tokenIndex
			{
				position241 := position
				if !_rules[ruleSymbolArg]() {
					goto l240
				}
			l242:
				{
					position243, tokenIndex243 := position, tokenIndex
					{
						position244, tokenIndex244 := position, tokenIndex
						if !_rules[ruleWS]() {
							goto l244
						}
						goto l245
					l244:
						position, tokenIndex = position244, tokenIndex244
					}
				l245:
					if buffer[position] != rune(',') {
						goto l243
					}
					position++
					{
						position246, tokenIndex246 := position, tokenIndex
						if !_rules[ruleWS]() {
							goto l246
						}
						goto l247
					l246:
						position, tokenIndex = position246, tokenIndex246
					}
				l247:
					if !_rules[ruleSymbolArg]() {
						goto l243
					}
					goto l242
				l243:
					position, tokenIndex = position243, tokenIndex243
				}
				add(ruleSymbolArgs, position241)
			}
			return true
		l240:
			position, tokenIndex = position240, tokenIndex240
			return false
		},
		/* 13 SymbolArg <- <(Offset / SymbolType / ((Offset / LocalSymbol / SymbolName / Dot) WS? Operator WS? (Offset / LocalSymbol / SymbolName)) / (LocalSymbol TCMarker?) / (SymbolName Offset) / (SymbolName TCMarker?))> */
		func() bool {
			position248, tokenIndex248 := position, tokenIndex
			{
				position249 := position
				{
					position250, tokenIndex250 := position, tokenIndex
					if !_rules[ruleOffset]() {
						goto l251
					}
					goto l250
				l251:
					position, tokenIndex = position250, tokenIndex250
					if !_rules[ruleSymbolType]() {
						goto l252
					}
					goto l250
				l252:
					position, tokenIndex = position250, tokenIndex250
					{
						position254, tokenIndex254 := position, tokenIndex
						if !_rules[ruleOffset]() {
							goto l255
						}
						goto l254
					l255:
						position, tokenIndex = position254, tokenIndex254
						if !_rules[ruleLocalSymbol]() {
							goto l256
						}
						goto l254
					l256:
						position, tokenIndex = position254, tokenIndex254
						if !_rules[ruleSymbolName]() {
							goto l257
						}
						goto l254
					l257:
						position, tokenIndex = position254, tokenIndex254
						if !_rules[ruleDot]() {
							goto l253
						}
					}
				l254:
					{
						position258, tokenIndex258 := position, tokenIndex
						if !_rules[ruleWS]() {
							goto l258
						}
						goto l259
					l258:
						position, tokenIndex = position258, tokenIndex258
					}
				l259:
					if !_rules[ruleOperator]() {
						goto l253
					}
					{
						position260, tokenIndex260 := position, tokenIndex
						if !_rules[ruleWS]() {
							goto l260
						}
						goto l261
					l260:
						position, tokenIndex = position260, tokenIndex260
					}
				l261:
					{
						position262, tokenIndex262 := position, tokenIndex
						if !_rules[ruleOffset]() {
							goto l263
						}
						goto l262
					l263:
						position, tokenIndex = position262, tokenIndex262
						if !_rules[ruleLocalSymbol]() {
							goto l264
						}
						goto l262
					l264:
						position, tokenIndex = position262, tokenIndex262
						if !_rules[ruleSymbolName]() {
							goto l253
						}
					}
				l262:
					goto l250
				l253:
					position, tokenIndex = position250, tokenIndex250
					if !_rules[ruleLocalSymbol]() {
						goto l265
					}
					{
						position266, tokenIndex266 := position, tokenIndex
						if !_rules[ruleTCMarker]() {
							goto l266
						}
						goto l267
					l266:
						position, tokenIndex = position266, tokenIndex266
					}
				l267:
					goto l250
				l265:
					position, tokenIndex = position250, tokenIndex250
					if !_rules[ruleSymbolName]() {
						goto l268
					}
					if !_rules[ruleOffset]() {
						goto l268
					}
					goto l250
				l268:
					position, tokenIndex = position250, tokenIndex250
					if !_rules[ruleSymbolName]() {
						goto l248
					}
					{
						position269, tokenIndex269 := position, tokenIndex
						if !_rules[ruleTCMarker]() {
							goto l269
						}
						goto l270
					l269:
						position, tokenIndex = position269, tokenIndex269
					}
				l270:
				}
			l250:
				add(ruleSymbolArg, position249)
			}
			return true
		l248:
			position, tokenIndex = position248, tokenIndex248
			return false
		},
		/* 14 SymbolType <- <(('@' 'f' 'u' 'n' 'c' 't' 'i' 'o' 'n') / ('@' 'o' 'b' 'j' 'e' 'c' 't'))> */
		func() bool {
			position271, tokenIndex271 := position, tokenIndex
			{
				position272 := position
				{
					position273, tokenIndex273 := position, tokenIndex
					if buffer[position] != rune('@') {
						goto l274
					}
					position++
					if buffer[position] != rune('f') {
						goto l274
					}
					position++
					if buffer[position] != rune('u') {
						goto l274
					}
					position++
					if buffer[position] != rune('n') {
						goto l274
					}
					position++
					if buffer[position] != rune('c') {
						goto l274
					}
					position++
					if buffer[position] != rune('t') {
						goto l274
					}
					position++
					if buffer[position] != rune('i') {
						goto l274
					}
					position++
					if buffer[position] != rune('o') {
						goto l274
					}
					position++
					if buffer[position] != rune('n') {
						goto l274
					}
					position++
					goto l273
				l274:
					position, tokenIndex = position273, tokenIndex273
					if buffer[position] != rune('@') {
						goto l271
					}
					position++
					if buffer[position] != rune('o') {
						goto l271
					}
					position++
					if buffer[position] != rune('b') {
						goto l271
					}
					position++
					if buffer[position] != rune('j') {
						goto l271
					}
					position++
					if buffer[position] != rune('e') {
						goto l271
					}
					position++
					if buffer[position] != rune('c') {
						goto l271
					}
					position++
					if buffer[position] != rune('t') {
						goto l271
					}
					position++
				}
			l273:
				add(ruleSymbolType, position272)
			}
			return true
		l271:
			position, tokenIndex = position271, tokenIndex271
			return false
		},
		/* 15 Dot <- <'.'> */
		func() bool {
			position275, tokenIndex275 := position, tokenIndex
			{
				position276 := position
				if buffer[position] != rune('.') {
					goto l275
				}
				position++
				add(ruleDot, position276)
			}
			return true
		l275:
			position, tokenIndex = position275, tokenIndex275
			return false
		},
		/* 16 TCMarker <- <('[' 'T' 'C' ']')> */
		func() bool {
			position277, tokenIndex277 := position, tokenIndex
			{
				position278 := position
				if buffer[position] != rune('[') {
					goto l277
				}
				position++
				if buffer[position] != rune('T') {
					goto l277
				}
				position++
				if buffer[position] != rune('C') {
					goto l277
				}
				position++
				if buffer[position] != rune(']') {
					goto l277
				}
				position++
				add(ruleTCMarker, position278)
			}
			return true
		l277:
			position, tokenIndex = position277, tokenIndex277
			return false
		},
		/* 17 EscapedChar <- <('\\' .)> */
		func() bool {
			position279, tokenIndex279 := position, tokenIndex
			{
				position280 := position
				if buffer[position] != rune('\\') {
					goto l279
				}
				position++
				if !matchDot() {
					goto l279
				}
				add(ruleEscapedChar, position280)
			}
			return true
		l279:
			position, tokenIndex = position279, tokenIndex279
			return false
		},
		/* 18 WS <- <(' ' / '\t')+> */
		func() bool {
			position281, tokenIndex281 := position, tokenIndex
			{
				position282 := position
				{
					position285, tokenIndex285 := position, tokenIndex
					if buffer[position] != rune(' ') {
						goto l286
					}
					position++
					goto l285
				l286:
					position, tokenIndex = position285, tokenIndex285
					if buffer[position] != rune('\t') {
						goto l281
					}
					position++
				}
			l285:
			l283:
				{
					position284, tokenIndex284 := position, tokenIndex
					{
						position287, tokenIndex287 := position, tokenIndex
						if buffer[position] != rune(' ') {
							goto l288
						}
						position++
						goto l287
					l288:
						position, tokenIndex = position287, tokenIndex287
						if buffer[position] != rune('\t') {
							goto l284
						}
						position++
					}
				l287:
					goto l283
				l284:
					position, tokenIndex = position284, tokenIndex284
				}
				add(ruleWS, position282)
			}
			return true
		l281:
			position, tokenIndex = position281, tokenIndex281
			return false
		},
		/* 19 Comment <- <('#' (!'\n' .)*)> */
		func() bool {
			position289, tokenIndex289 := position, tokenIndex
			{
				position290 := position
				if buffer[position] != rune('#') {
					goto l289
				}
				position++
			l291:
				{
					position292, tokenIndex292 := position, tokenIndex
					{
						position293, tokenIndex293 := position, tokenIndex
						if buffer[position] != rune('\n') {
							goto l293
						}
						position++
						goto l292
					l293:
						position, tokenIndex = position293, tokenIndex293
					}
					if !matchDot() {
						goto l292
					}
					goto l291
				l292:
					position, tokenIndex = position292, tokenIndex292
				}
				add(ruleComment, position290)
			}
			return true
		l289:
			position, tokenIndex = position289, tokenIndex289
			return false
		},
		/* 20 Label <- <((LocalSymbol / LocalLabel / SymbolName) ':')> */
		func() bool {
			position294, tokenIndex294 := position, tokenIndex
			{
				position295 := position
				{
					position296, tokenIndex296 := position, tokenIndex
					if !_rules[ruleLocalSymbol]() {
						goto l297
					}
					goto l296
				l297:
					position, tokenIndex = position296, tokenIndex296
					if !_rules[ruleLocalLabel]() {
						goto l298
					}
					goto l296
				l298:
					position, tokenIndex = position296, tokenIndex296
					if !_rules[ruleSymbolName]() {
						goto l294
					}
				}
			l296:
				if buffer[position] != rune(':') {
					goto l294
				}
				position++
				add(ruleLabel, position295)
			}
			return true
		l294:
			position, tokenIndex = position294, tokenIndex294
			return false
		},
		/* 21 SymbolName <- <(([a-z] / [A-Z] / '.' / '_') ([a-z] / [A-Z] / '.' / ([0-9] / [0-9]) / '$' / '_')*)> */
		func() bool {
			position299, tokenIndex299 := position, tokenIndex
			{
				position300 := position
				{
					position301, tokenIndex301 := position, tokenIndex
					if c := buffer[position]; c < rune('a') || c > rune('z') {
						goto l302
					}
					position++
					goto l301
				l302:
					position, tokenIndex = position301, tokenIndex301
					if c := buffer[position]; c < rune('A') || c > rune('Z') {
						goto l303
					}
					position++
					goto l301
				l303:
					position, tokenIndex = position301, tokenIndex301
					if buffer[position] != rune('.') {
						goto l304
					}
					position++
					goto l301
				l304:
					position, tokenIndex = position301, tokenIndex301
					if buffer[position] != rune('_') {
						goto l299
					}
					position++
				}
			l301:
			l305:
				{
					position306, tokenIndex306 := position, tokenIndex
					{
						position307, tokenIndex307 := position, tokenIndex
						if c := buffer[position]; c < rune('a') || c > rune('z') {
							goto l308
						}
						position++
						goto l307
					l308:
						position, tokenIndex = position307, tokenIndex307
						if c := buffer[position]; c < rune('A') || c > rune('Z') {
							goto l309
						}
						position++
						goto l307
					l309:
						position, tokenIndex = position307, tokenIndex307
						if buffer[position] != rune('.') {
							goto l310
						}
						position++
						goto l307
					l310:
						position, tokenIndex = position307, tokenIndex307
						{
							position312, tokenIndex312 := position, tokenIndex
							if c := buffer[position]; c < rune('0') || c > rune('9') {
								goto l313
							}
							position++
							goto l312
						l313:
							position, tokenIndex = position312, tokenIndex312
							if c := buffer[position]; c < rune('0') || c > rune('9') {
								goto l311
							}
							position++
						}
					l312:
						goto l307
					l311:
						position, tokenIndex = position307, tokenIndex307
						if buffer[position] != rune('$') {
							goto l314
						}
						position++
						goto l307
					l314:
						position, tokenIndex = position307, tokenIndex307
						if buffer[position] != rune('_') {
							goto l306
						}
						position++
					}
				l307:
					goto l305
				l306:
					position, tokenIndex = position306, tokenIndex306
				}
				add(ruleSymbolName, position300)
			}
			return true
		l299:
			position, tokenIndex = position299, tokenIndex299
			return false
		},
		/* 22 LocalSymbol <- <('.' 'L' ([a-z] / [A-Z] / '.' / ([0-9] / [0-9]) / '$' / '_')+)> */
		func() bool {
			position315, tokenIndex315 := position, tokenIndex
			{
				position316 := position
				if buffer[position] != rune('.') {
					goto l315
				}
				position++
				if buffer[position] != rune('L') {
					goto l315
				}
				position++
				{
					position319, tokenIndex319 := position, tokenIndex
					if c := buffer[position]; c < rune('a') || c > rune('z') {
						goto l320
					}
					position++
					goto l319
				l320:
					position, tokenIndex = position319, tokenIndex319
					if c := buffer[position]; c < rune('A') || c > rune('Z') {
						goto l321
					}
					position++
					goto l319
				l321:
					position, tokenIndex = position319, tokenIndex319
					if buffer[position] != rune('.') {
						goto l322
					}
					position++
					goto l319
				l322:
					position, tokenIndex = position319, tokenIndex319
					{
						position324, tokenIndex324 := position, tokenIndex
						if c := buffer[position]; c < rune('0') || c > rune('9') {
							goto l325
						}
						position++
						goto l324
					l325:
						position, tokenIndex = position324, tokenIndex324
						if c := buffer[position]; c < rune('0') || c > rune('9') {
							goto l323
						}
						position++
					}
				l324:
					goto l319
				l323:
					position, tokenIndex = position319, tokenIndex319
					if buffer[position] != rune('$') {
						goto l326
					}
					position++
					goto l319
				l326:
					position, tokenIndex = position319, tokenIndex319
					if buffer[position] != rune('_') {
						goto l315
					}
					position++
				}
			l319:
			l317:
				{
					position318, tokenIndex318 := position, tokenIndex
					{
						position327, tokenIndex327 := position, tokenIndex
						if c := buffer[position]; c < rune('a') || c > rune('z') {
							goto l328
						}
						position++
						goto l327
					l328:
						position, tokenIndex = position327, tokenIndex327
						if c := buffer[position]; c < rune('A') || c > rune('Z') {
							goto l329
						}
						position++
						goto l327
					l329:
						position, tokenIndex = position327, tokenIndex327
						if buffer[position] != rune('.') {
							goto l330
						}
						position++
						goto l327
					l330:
						position, tokenIndex = position327, tokenIndex327
						{
							position332, tokenIndex332 := position, tokenIndex
							if c := buffer[position]; c < rune('0') || c > rune('9') {
								goto l333
							}
							position++
							goto l332
						l333:
							position, tokenIndex = position332, tokenIndex332
							if c := buffer[position]; c < rune('0') || c > rune('9') {
								goto l331
							}
							position++
						}
					l332:
						goto l327
					l331:
						position, tokenIndex = position327, tokenIndex327
						if buffer[position] != rune('$') {
							goto l334
						}
						position++
						goto l327
					l334:
						position, tokenIndex = position327, tokenIndex327
						if buffer[position] != rune('_') {
							goto l318
						}
						position++
					}
				l327:
					goto l317
				l318:
					position, tokenIndex = position318, tokenIndex318
				}
				add(ruleLocalSymbol, position316)
			}
			return true
		l315:
			position, tokenIndex = position315, tokenIndex315
			return false
		},
		/* 23 LocalLabel <- <([0-9] ([0-9] / '$')*)> */
		func() bool {
			position335, tokenIndex335 := position, tokenIndex
			{
				position336 := position
				if c := buffer[position]; c < rune('0') || c > rune('9') {
					goto l335
				}
				position++
			l337:
				{
					position338, tokenIndex338 := position, tokenIndex
					{
						position339, tokenIndex339 := position, tokenIndex
						if c := buffer[position]; c < rune('0') || c > rune('9') {
							goto l340
						}
						position++
						goto l339
					l340:
						position, tokenIndex = position339, tokenIndex339
						if buffer[position] != rune('$') {
							goto l338
						}
						position++
					}
				l339:
					goto l337
				l338:
					position, tokenIndex = position338, tokenIndex338
				}
				add(ruleLocalLabel, position336)
			}
			return true
		l335:
			position, tokenIndex = position335, tokenIndex335
			return false
		},
		/* 24 LocalLabelRef <- <([0-9] ([0-9] / '$')* ('b' / 'f'))> */
		func() bool {
			position341, tokenIndex341 := position, tokenIndex
			{
				position342 := position
				if c := buffer[position]; c < rune('0') || c > rune('9') {
					goto l341
				}
				position++
			l343:
				{
					position344, tokenIndex344 := position, tokenIndex
					{
						position345, tokenIndex345 := position, tokenIndex
						if c := buffer[position]; c < rune('0') || c > rune('9') {
							goto l346
						}
						position++
						goto l345
					l346:
						position, tokenIndex = position345, tokenIndex345
						if buffer[position] != rune('$') {
							goto l344
						}
						position++
					}
				l345:
					goto l343
				l344:
					position, tokenIndex = position344, tokenIndex344
				}
				{
					position347, tokenIndex347 := position, tokenIndex
					if buffer[position] != rune('b') {
						goto l348
					}
					position++
					goto l347
				l348:
					position, tokenIndex = position347, tokenIndex347
					if buffer[position] != rune('f') {
						goto l341
					}
					position++
				}
			l347:
				add(ruleLocalLabelRef, position342)
			}
			return true
		l341:
			position, tokenIndex = position341, tokenIndex341
			return false
		},
		/* 25 Instruction <- <(InstructionName (WS InstructionArg (WS? ',' WS? InstructionArg)*)?)> */
		func() bool {
			position349, tokenIndex349 := position, tokenIndex
			{
				position350 := position
				if !_rules[ruleInstructionName]() {
					goto l349
				}
				{
					position351, tokenIndex351 := position, tokenIndex
					if !_rules[ruleWS]() {
						goto l351
					}
					if !_rules[ruleInstructionArg]() {
						goto l351
					}
				l353:
					{
						position354, tokenIndex354 := position, tokenIndex
						{
							position355, tokenIndex355 := position, tokenIndex
							if !_rules[ruleWS]() {
								goto l355
							}
							goto l356
						l355:
							position, tokenIndex = position355, tokenIndex355
						}
					l356:
						if buffer[position] != rune(',') {
							goto l354
						}
						position++
						{
							position357, tokenIndex357 := position, tokenIndex
							if !_rules[ruleWS]() {
								goto l357
							}
							goto l358
						l357:
							position, tokenIndex = position357, tokenIndex357
						}
					l358:
						if !_rules[ruleInstructionArg]() {
							goto l354
						}
						goto l353
					l354:
						position, tokenIndex = position354, tokenIndex354
					}
					goto l352
				l351:
					position, tokenIndex = position351, tokenIndex351
				}
			l352:
				add(ruleInstruction, position350)
			}
			return true
		l349:
			position, tokenIndex = position349, tokenIndex349
			return false
		},
		/* 26 InstructionName <- <(([a-z] / [A-Z]) ([a-z] / [A-Z] / ([0-9] / [0-9]))* ('.' / '+' / '-')?)> */
		func() bool {
			position359, tokenIndex359 := position, tokenIndex
			{
				position360 := position
				{
					position361, tokenIndex361 := position, tokenIndex
					if c := buffer[position]; c < rune('a') || c > rune('z') {
						goto l362
					}
					position++
					goto l361
				l362:
					position, tokenIndex = position361, tokenIndex361
					if c := buffer[position]; c < rune('A') || c > rune('Z') {
						goto l359
					}
					position++
				}
			l361:
			l363:
				{
					position364, tokenIndex364 := position, tokenIndex
					{
						position365, tokenIndex365 := position, tokenIndex
						if c := buffer[position]; c < rune('a') || c > rune('z') {
							goto l366
						}
						position++
						goto l365
					l366:
						position, tokenIndex = position365, tokenIndex365
						if c := buffer[position]; c < rune('A') || c > rune('Z') {
							goto l367
						}
						position++
						goto l365
					l367:
						position, tokenIndex = position365, tokenIndex365
						{
							position368, tokenIndex368 := position, tokenIndex
							if c := buffer[position]; c < rune('0') || c > rune('9') {
								goto l369
							}
							position++
							goto l368
						l369:
							position, tokenIndex = position368, tokenIndex368
							if c := buffer[position]; c < rune('0') || c > rune('9') {
								goto l364
							}
							position++
						}
					l368:
					}
				l365:
					goto l363
				l364:
					position, tokenIndex = position364, tokenIndex364
				}
				{
					position370, tokenIndex370 := position, tokenIndex
					{
						position372, tokenIndex372 := position, tokenIndex
						if buffer[position] != rune('.') {
							goto l373
						}
						position++
						goto l372
					l373:
						position, tokenIndex = position372, tokenIndex372
						if buffer[position] != rune('+') {
							goto l374
						}
						position++
						goto l372
					l374:
						position, tokenIndex = position372, tokenIndex372
						if buffer[position] != rune('-') {
							goto l370
						}
						position++
					}
				l372:
					goto l371
				l370:
					position, tokenIndex = position370, tokenIndex370
				}
			l371:
				add(ruleInstructionName, position360)
			}
			return true
		l359:
			position, tokenIndex = position359, tokenIndex359
			return false
		},
		/* 27 InstructionArg <- <(IndirectionIndicator? (RegisterOrConstant / LocalLabelRef / TOCRefHigh / TOCRefLow / MemoryRef) AVX512Token*)> */
		func() bool {
			position375, tokenIndex375 := position, tokenIndex
			{
				position376 := position
				{
					position377, tokenIndex377 := position, tokenIndex
					if !_rules[ruleIndirectionIndicator]() {
						goto l377
					}
					goto l378
				l377:
					position, tokenIndex = position377, tokenIndex377
				}
			l378:
				{
					position379, tokenIndex379 := position, tokenIndex
					if !_rules[ruleRegisterOrConstant]() {
						goto l380
					}
					goto l379
				l380:
					position, tokenIndex = position379, tokenIndex379
					if !_rules[ruleLocalLabelRef]() {
						goto l381
					}
					goto l379
				l381:
					position, tokenIndex = position379, tokenIndex379
					if !_rules[ruleTOCRefHigh]() {
						goto l382
					}
					goto l379
				l382:
					position, tokenIndex = position379, tokenIndex379
					if !_rules[ruleTOCRefLow]() {
						goto l383
					}
					goto l379
				l383:
					position, tokenIndex = position379, tokenIndex379
					if !_rules[ruleMemoryRef]() {
						goto l375
					}
				}
			l379:
			l384:
				{
					position385, tokenIndex385 := position, tokenIndex
					if !_rules[ruleAVX512Token]() {
						goto l385
					}
					goto l384
				l385:
					position, tokenIndex = position385, tokenIndex385
				}
				add(ruleInstructionArg, position376)
			}
			return true
		l375:
			position, tokenIndex = position375, tokenIndex375
			return false
		},
		/* 28 AVX512Token <- <(WS? '{' '%'? ([0-9] / [a-z])* '}')> */
		func() bool {
			position386, tokenIndex386 := position, tokenIndex
			{
				position387 := position
				{
					position388, tokenIndex388 := position, tokenIndex
					if !_rules[ruleWS]() {
						goto l388
					}
					goto l389
				l388:
					position, tokenIndex = position388, tokenIndex388
				}
			l389:
				if buffer[position] != rune('{') {
					goto l386
				}
				position++
				{
					position390, tokenIndex390 := position, tokenIndex
					if buffer[position] != rune('%') {
						goto l390
					}
					position++
					goto l391
				l390:
					position, tokenIndex = position390, tokenIndex390
				}
			l391:
			l392:
				{
					position393, tokenIndex393 := position, tokenIndex
					{
						position394, tokenIndex394 := position, tokenIndex
						if c := buffer[position]; c < rune('0') || c > rune('9') {
							goto l395
						}
						position++
						goto l394
					l395:
						position, tokenIndex = position394, tokenIndex394
						if c := buffer[position]; c < rune('a') || c > rune('z') {
							goto l393
						}
						position++
					}
				l394:
					goto l392
				l393:
					position, tokenIndex = position393, tokenIndex393
				}
				if buffer[position] != rune('}') {
					goto l386
				}
				position++
				add(ruleAVX512Token, position387)
			}
			return true
		l386:
			position, tokenIndex = position386, tokenIndex386
			return false
		},
		/* 29 TOCRefHigh <- <('.' 'T' 'O' 'C' '.' '-' (('0' 'b') / ('.' 'L' ([a-z] / [A-Z] / '_' / [0-9])+)) ('@' ('h' / 'H') ('a' / 'A')))> */
		func() bool {
			position396, tokenIndex396 := position, tokenIndex
			{
				position397 := position
				if buffer[position] != rune('.') {
					goto l396
				}
				position++
				if buffer[position] != rune('T') {
					goto l396
				}
				position++
				if buffer[position] != rune('O') {
					goto l396
				}
				position++
				if buffer[position] != rune('C') {
					goto l396
				}
				position++
				if buffer[position] != rune('.') {
					goto l396
				}
				position++
				if buffer[position] != rune('-') {
					goto l396
				}
				position++
				{
					position398, tokenIndex398 := position, tokenIndex
					if buffer[position] != rune('0') {
						goto l399
					}
					position++
					if buffer[position] != rune('b') {
						goto l399
					}
					position++
					goto l398
				l399:
					position, tokenIndex = position398, tokenIndex398
					if buffer[position] != rune('.') {
						goto l396
					}
					position++
					if buffer[position] != rune('L') {
						goto l396
					}
					position++
					{
						position402, tokenIndex402 := position, tokenIndex
						if c := buffer[position]; c < rune('a') || c > rune('z') {
							goto l403
						}
						position++
						goto l402
					l403:
						position, tokenIndex = position402, tokenIndex402
						if c := buffer[position]; c < rune('A') || c > rune('Z') {
							goto l404
						}
						position++
						goto l402
					l404:
						position, tokenIndex = position402, tokenIndex402
						if buffer[position] != rune('_') {
							goto l405
						}
						position++
						goto l402
					l405:
						position, tokenIndex = position402, tokenIndex402
						if c := buffer[position]; c < rune('0') || c > rune('9') {
							goto l396
						}
						position++
					}
				l402:
				l400:
					{
						position401, tokenIndex401 := position, tokenIndex
						{
							position406, tokenIndex406 := position, tokenIndex
							if c := buffer[position]; c < rune('a') || c > rune('z') {
								goto l407
							}
							position++
							goto l406
						l407:
							position, tokenIndex = position406, tokenIndex406
							if c := buffer[position]; c < rune('A') || c > rune('Z') {
								goto l408
							}
							position++
							goto l406
						l408:
							position, tokenIndex = position406, tokenIndex406
							if buffer[position] != rune('_') {
								goto l409
							}
							position++
							goto l406
						l409:
							position, tokenIndex = position406, tokenIndex406
							if c := buffer[position]; c < rune('0') || c > rune('9') {
								goto l401
							}
							position++
						}
					l406:
						goto l400
					l401:
						position, tokenIndex = position401, tokenIndex401
					}
				}
			l398:
				if buffer[position] != rune('@') {
					goto l396
				}
				position++
				{
					position410, tokenIndex410 := position, tokenIndex
					if buffer[position] != rune('h') {
						goto l411
					}
					position++
					goto l410
				l411:
					position, tokenIndex = position410, tokenIndex410
					if buffer[position] != rune('H') {
						goto l396
					}
					position++
				}
			l410:
				{
					position412, tokenIndex412 := position, tokenIndex
					if buffer[position] != rune('a') {
						goto l413
					}
					position++
					goto l412
				l413:
					position, tokenIndex = position412, tokenIndex412
					if buffer[position] != rune('A') {
						goto l396
					}
					position++
				}
			l412:
				add(ruleTOCRefHigh, position397)
			}
			return true
		l396:
			position, tokenIndex = position396, tokenIndex396
			return false
		},
		/* 30 TOCRefLow <- <('.' 'T' 'O' 'C' '.' '-' (('0' 'b') / ('.' 'L' ([a-z] / [A-Z] / '_' / [0-9])+)) ('@' ('l' / 'L')))> */
		func() bool {
			position414, tokenIndex414 := position, tokenIndex
			{
				position415 := position
				if buffer[position] != rune('.') {
					goto l414
				}
				position++
				if buffer[position] != rune('T') {
					goto l414
				}
				position++
				if buffer[position] != rune('O') {
					goto l414
				}
				position++
				if buffer[position] != rune('C') {
					goto l414
				}
				position++
				if buffer[position] != rune('.') {
					goto l414
				}
				position++
				if buffer[position] != rune('-') {
					goto l414
				}
				position++
				{
					position416, tokenIndex416 := position, tokenIndex
					if buffer[position] != rune('0') {
						goto l417
					}
					position++
					if buffer[position] != rune('b') {
						goto l417
					}
					position++
					goto l416
				l417:
					position, tokenIndex = position416, tokenIndex416
					if buffer[position] != rune('.') {
						goto l414
					}
					position++
					if buffer[position] != rune('L') {
						goto l414
					}
					position++
					{
						position420, tokenIndex420 := position, tokenIndex
						if c := buffer[position]; c < rune('a') || c > rune('z') {
							goto l421
						}
						position++
						goto l420
					l421:
						position, tokenIndex = position420, tokenIndex420
						if c := buffer[position]; c < rune('A') || c > rune('Z') {
							goto l422
						}
						position++
						goto l420
					l422:
						position, tokenIndex = position420, tokenIndex420
						if buffer[position] != rune('_') {
							goto l423
						}
						position++
						goto l420
					l423:
						position, tokenIndex = position420, tokenIndex420
						if c := buffer[position]; c < rune('0') || c > rune('9') {
							goto l414
						}
						position++
					}
				l420:
				l418:
					{
						position419, tokenIndex419 := position, tokenIndex
						{
							position424, tokenIndex424 := position, tokenIndex
							if c := buffer[position]; c < rune('a') || c > rune('z') {
								goto l425
							}
							position++
							goto l424
						l425:
							position, tokenIndex = position424, tokenIndex424
							if c := buffer[position]; c < rune('A') || c > rune('Z') {
								goto l426
							}
							position++
							goto l424
						l426:
							position, tokenIndex = position424, tokenIndex424
							if buffer[position] != rune('_') {
								goto l427
							}
							position++
							goto l424
						l427:
							position, tokenIndex = position424, tokenIndex424
							if c := buffer[position]; c < rune('0') || c > rune('9') {
								goto l419
							}
							position++
						}
					l424:
						goto l418
					l419:
						position, tokenIndex = position419, tokenIndex419
					}
				}
			l416:
				if buffer[position] != rune('@') {
					goto l414
				}
				position++
				{
					position428, tokenIndex428 := position, tokenIndex
					if buffer[position] != rune('l') {
						goto l429
					}
					position++
					goto l428
				l429:
					position, tokenIndex = position428, tokenIndex428
					if buffer[position] != rune('L') {
						goto l414
					}
					position++
				}
			l428:
				add(ruleTOCRefLow, position415)
			}
			return true
		l414:
			position, tokenIndex = position414, tokenIndex414
			return false
		},
		/* 31 IndirectionIndicator <- <'*'> */
		func() bool {
			position430, tokenIndex430 := position, tokenIndex
			{
				position431 := position
				if buffer[position] != rune('*') {
					goto l430
				}
				position++
				add(ruleIndirectionIndicator, position431)
			}
			return true
		l430:
			position, tokenIndex = position430, tokenIndex430
			return false
		},
		/* 32 RegisterOrConstant <- <((('%' ([a-z] / [A-Z]) ([a-z] / [A-Z] / ([0-9] / [0-9]))*) / ('$'? ((Offset Offset) / Offset))) !('f' / 'b' / ':' / '(' / '+' / '-'))> */
		func() bool {
			position432, tokenIndex432 := position, tokenIndex
			{
				position433 := position
				{
					position434, tokenIndex434 := position, tokenIndex
					if buffer[position] != rune('%') {
						goto l435
					}
					position++
					{
						position436, tokenIndex436 := position, tokenIndex
						if c := buffer[position]; c < rune('a') || c > rune('z') {
							goto l437
						}
						position++
						goto l436
					l437:
						position, tokenIndex = position436, tokenIndex436
						if c := buffer[position]; c < rune('A') || c > rune('Z') {
							goto l435
						}
						position++
					}
				l436:
				l438:
					{
						position439, tokenIndex439 := position, tokenIndex
						{
							position440, tokenIndex440 := position, tokenIndex
							if c := buffer[position]; c < rune('a') || c > rune('z') {
								goto l441
							}
							position++
							goto l440
						l441:
							position, tokenIndex = position440, tokenIndex440
							if c := buffer[position]; c < rune('A') || c > rune('Z') {
								goto l442
							}
							position++
							goto l440
						l442:
							position, tokenIndex = position440, tokenIndex440
							{
								position443, tokenIndex443 := position, tokenIndex
								if c := buffer[position]; c < rune('0') || c > rune('9') {
									goto l444
								}
								position++
								goto l443
							l444:
								position, tokenIndex = position443, tokenIndex443
								if c := buffer[position]; c < rune('0') || c > rune('9') {
									goto l439
								}
								position++
							}
						l443:
						}
					l440:
						goto l438
					l439:
						position, tokenIndex = position439, tokenIndex439
					}
					goto l434
				l435:
					position, tokenIndex = position434, tokenIndex434
					{
						position445, tokenIndex445 := position, tokenIndex
						if buffer[position] != rune('$') {
							goto l445
						}
						position++
						goto l446
					l445:
						position, tokenIndex = position445, tokenIndex445
					}
				l446:
					{
						position447, tokenIndex447 := position, tokenIndex
						if !_rules[ruleOffset]() {
							goto l448
						}
						if !_rules[ruleOffset]() {
							goto l448
						}
						goto l447
					l448:
						position, tokenIndex = position447, tokenIndex447
						if !_rules[ruleOffset]() {
							goto l432
						}
					}
				l447:
				}
			l434:
				{
					position449, tokenIndex449 := position, tokenIndex
					{
						position450, tokenIndex450 := position, tokenIndex
						if buffer[position] != rune('f') {
							goto l451
						}
						position++
						goto l450
					l451:
						position, tokenIndex = position450, tokenIndex450
						if buffer[position] != rune('b') {
							goto l452
						}
						position++
						goto l450
					l452:
						position, tokenIndex = position450, tokenIndex450
						if buffer[position] != rune(':') {
							goto l453
						}
						position++
						goto l450
					l453:
						position, tokenIndex = position450, tokenIndex450
						if buffer[position] != rune('(') {
							goto l454
						}
						position++
						goto l450
					l454:
						position, tokenIndex = position450, tokenIndex450
						if buffer[position] != rune('+') {
							goto l455
						}
						position++
						goto l450
					l455:
						position, tokenIndex = position450, tokenIndex450
						if buffer[position] != rune('-') {
							goto l449
						}
						position++
					}
				l450:
					goto l432
				l449:
					position, tokenIndex = position449, tokenIndex449
				}
				add(ruleRegisterOrConstant, position433)
			}
			return true
		l432:
			position, tokenIndex = position432, tokenIndex432
			return false
		},
		/* 33 MemoryRef <- <((SymbolRef BaseIndexScale) / SymbolRef / (Offset* BaseIndexScale) / (SegmentRegister Offset BaseIndexScale) / (SegmentRegister BaseIndexScale) / (SegmentRegister Offset) / BaseIndexScale)> */
		func() bool {
			position456, tokenIndex456 := position, tokenIndex
			{
				position457 := position
				{
					position458, tokenIndex458 := position, tokenIndex
					if !_rules[ruleSymbolRef]() {
						goto l459
					}
					if !_rules[ruleBaseIndexScale]() {
						goto l459
					}
					goto l458
				l459:
					position, tokenIndex = position458, tokenIndex458
					if !_rules[ruleSymbolRef]() {
						goto l460
					}
					goto l458
				l460:
					position, tokenIndex = position458, tokenIndex458
				l462:
					{
						position463, tokenIndex463 := position, tokenIndex
						if !_rules[ruleOffset]() {
							goto l463
						}
						goto l462
					l463:
						position, tokenIndex = position463, tokenIndex463
					}
					if !_rules[ruleBaseIndexScale]() {
						goto l461
					}
					goto l458
				l461:
					position, tokenIndex = position458, tokenIndex458
					if !_rules[ruleSegmentRegister]() {
						goto l464
					}
					if !_rules[ruleOffset]() {
						goto l464
					}
					if !_rules[ruleBaseIndexScale]() {
						goto l464
					}
					goto l458
				l464:
					position, tokenIndex = position458, tokenIndex458
					if !_rules[ruleSegmentRegister]() {
						goto l465
					}
					if !_rules[ruleBaseIndexScale]() {
						goto l465
					}
					goto l458
				l465:
					position, tokenIndex = position458, tokenIndex458
					if !_rules[ruleSegmentRegister]() {
						goto l466
					}
					if !_rules[ruleOffset]() {
						goto l466
					}
					goto l458
				l466:
					position, tokenIndex = position458, tokenIndex458
					if !_rules[ruleBaseIndexScale]() {
						goto l456
					}
				}
			l458:
				add(ruleMemoryRef, position457)
			}
			return true
		l456:
			position, tokenIndex = position456, tokenIndex456
			return false
		},
		/* 34 SymbolRef <- <((Offset* '+')? (LocalSymbol / SymbolName) Offset* ('@' Section Offset*)?)> */
		func() bool {
			position467, tokenIndex467 := position, tokenIndex
			{
				position468 := position
				{
					position469, tokenIndex469 := position, tokenIndex
				l471:
					{
						position472, tokenIndex472 := position, tokenIndex
						if !_rules[ruleOffset]() {
							goto l472
						}
						goto l471
					l472:
						position, tokenIndex = position472, tokenIndex472
					}
					if buffer[position] != rune('+') {
						goto l469
					}
					position++
					goto l470
				l469:
					position, tokenIndex = position469, tokenIndex469
				}
			l470:
				{
					position473, tokenIndex473 := position, tokenIndex
					if !_rules[ruleLocalSymbol]() {
						goto l474
					}
					goto l473
				l474:
					position, tokenIndex = position473, tokenIndex473
					if !_rules[ruleSymbolName]() {
						goto l467
					}
				}
			l473:
			l475:
				{
					position476, tokenIndex476 := position, tokenIndex
					if !_rules[ruleOffset]() {
						goto l476
					}
					goto l475
				l476:
					position, tokenIndex = position476, tokenIndex476
				}
				{
					position477, tokenIndex477 := position, tokenIndex
					if buffer[position] != rune('@') {
						goto l477
					}
					position++
					if !_rules[ruleSection]() {
						goto l477
					}
				l479:
					{
						position480, tokenIndex480 := position, tokenIndex
						if !_rules[ruleOffset]() {
							goto l480
						}
						goto l479
					l480:
						position, tokenIndex = position480, tokenIndex480
					}
					goto l478
				l477:
					position, tokenIndex = position477, tokenIndex477
				}
			l478:
				add(ruleSymbolRef, position468)
			}
			return true
		l467:
			position, tokenIndex = position467, tokenIndex467
			return false
		},
		/* 35 BaseIndexScale <- <('(' RegisterOrConstant? WS? (',' WS? RegisterOrConstant WS? (',' [0-9]+)?)? ')')> */
		func() bool {
			position481, tokenIndex481 := position, tokenIndex
			{
				position482 := position
				if buffer[position] != rune('(') {
					goto l481
				}
				position++
				{
					position483, tokenIndex483 := position, tokenIndex
					if !_rules[ruleRegisterOrConstant]() {
						goto l483
					}
					goto l484
				l483:
					position, tokenIndex = position483, tokenIndex483
				}
			l484:
				{
					position485, tokenIndex485 := position, tokenIndex
					if !_rules[ruleWS]() {
						goto l485
					}
					goto l486
				l485:
					position, tokenIndex = position485, tokenIndex485
				}
			l486:
				{
					position487, tokenIndex487 := position, tokenIndex
					if buffer[position] != rune(',') {
						goto l487
					}
					position++
					{
						position489, tokenIndex489 := position, tokenIndex
						if !_rules[ruleWS]() {
							goto l489
						}
						goto l490
					l489:
						position, tokenIndex = position489, tokenIndex489
					}
				l490:
					if !_rules[ruleRegisterOrConstant]() {
						goto l487
					}
					{
						position491, tokenIndex491 := position, tokenIndex
						if !_rules[ruleWS]() {
							goto l491
						}
						goto l492
					l491:
						position, tokenIndex = position491, tokenIndex491
					}
				l492:
					{
						position493, tokenIndex493 := position, tokenIndex
						if buffer[position] != rune(',') {
							goto l493
						}
						position++
						if c := buffer[position]; c < rune('0') || c > rune('9') {
							goto l493
						}
						position++
					l495:
						{
							position496, tokenIndex496 := position, tokenIndex
							if c := buffer[position]; c < rune('0') || c > rune('9') {
								goto l496
							}
							position++
							goto l495
						l496:
							position, tokenIndex = position496, tokenIndex496
						}
						goto l494
					l493:
						position, tokenIndex = position493, tokenIndex493
					}
				l494:
					goto l488
				l487:
					position, tokenIndex = position487, tokenIndex487
				}
			l488:
				if buffer[position] != rune(')') {
					goto l481
				}
				position++
				add(ruleBaseIndexScale, position482)
			}
			return true
		l481:
			position, tokenIndex = position481, tokenIndex481
			return false
		},
		/* 36 Operator <- <('+' / '-')> */
		func() bool {
			position497, tokenIndex497 := position, tokenIndex
			{
				position498 := position
				{
					position499, tokenIndex499 := position, tokenIndex
					if buffer[position] != rune('+') {
						goto l500
					}
					position++
					goto l499
				l500:
					position, tokenIndex = position499, tokenIndex499
					if buffer[position] != rune('-') {
						goto l497
					}
					position++
				}
			l499:
				add(ruleOperator, position498)
			}
			return true
		l497:
			position, tokenIndex = position497, tokenIndex497
			return false
		},
		/* 37 Offset <- <('+'? '-'? (('0' ('b' / 'B') ('0' / '1')+) / ('0' ('x' / 'X') ([0-9] / [0-9] / ([a-f] / [A-F]))+) / [0-9]+))> */
		func() bool {
			position501, tokenIndex501 := position, tokenIndex
			{
				position502 := position
				{
					position503, tokenIndex503 := position, tokenIndex
					if buffer[position] != rune('+') {
						goto l503
					}
					position++
					goto l504
				l503:
					position, tokenIndex = position503, tokenIndex503
				}
			l504:
				{
					position505, tokenIndex505 := position, tokenIndex
					if buffer[position] != rune('-') {
						goto l505
					}
					position++
					goto l506
				l505:
					position, tokenIndex = position505, tokenIndex505
				}
			l506:
				{
					position507, tokenIndex507 := position, tokenIndex
					if buffer[position] != rune('0') {
						goto l508
					}
					position++
					{
						position509, tokenIndex509 := position, tokenIndex
						if buffer[position] != rune('b') {
							goto l510
						}
						position++
						goto l509
					l510:
						position, tokenIndex = position509, tokenIndex509
						if buffer[position] != rune('B') {
							goto l508
						}
						position++
					}
				l509:
					{
						position513, tokenIndex513 := position, tokenIndex
						if buffer[position] != rune('0') {
							goto l514
						}
						position++
						goto l513
					l514:
						position, tokenIndex = position513, tokenIndex513
						if buffer[position] != rune('1') {
							goto l508
						}
						position++
					}
				l513:
				l511:
					{
						position512, tokenIndex512 := position, tokenIndex
						{
							position515, tokenIndex515 := position, tokenIndex
							if buffer[position] != rune('0') {
								goto l516
							}
							position++
							goto l515
						l516:
							position, tokenIndex = position515, tokenIndex515
							if buffer[position] != rune('1') {
								goto l512
							}
							position++
						}
					l515:
						goto l511
					l512:
						position, tokenIndex = position512, tokenIndex512
					}
					goto l507
				l508:
					position, tokenIndex = position507, tokenIndex507
					if buffer[position] != rune('0') {
						goto l517
					}
					position++
					{
						position518, tokenIndex518 := position, tokenIndex
						if buffer[position] != rune('x') {
							goto l519
						}
						position++
						goto l518
					l519:
						position, tokenIndex = position518, tokenIndex518
						if buffer[position] != rune('X') {
							goto l517
						}
						position++
					}
				l518:
					{
						position522, tokenIndex522 := position, tokenIndex
						if c := buffer[position]; c < rune('0') || c > rune('9') {
							goto l523
						}
						position++
						goto l522
					l523:
						position, tokenIndex = position522, tokenIndex522
						if c := buffer[position]; c < rune('0') || c > rune('9') {
							goto l524
						}
						position++
						goto l522
					l524:
						position, tokenIndex = position522, tokenIndex522
						{
							position525, tokenIndex525 := position, tokenIndex
							if c := buffer[position]; c < rune('a') || c > rune('f') {
								goto l526
							}
							position++
							goto l525
						l526:
							position, tokenIndex = position525, tokenIndex525
							if c := buffer[position]; c < rune('A') || c > rune('F') {
								goto l517
							}
							position++
						}
					l525:
					}
				l522:
				l520:
					{
						position521, tokenIndex521 := position, tokenIndex
						{
							position527, tokenIndex527 := position, tokenIndex
							if c := buffer[position]; c < rune('0') || c > rune('9') {
								goto l528
							}
							position++
							goto l527
						l528:
							position, tokenIndex = position527, tokenIndex527
							if c := buffer[position]; c < rune('0') || c > rune('9') {
								goto l529
							}
							position++
							goto l527
						l529:
							position, tokenIndex = position527, tokenIndex527
							{
								position530, tokenIndex530 := position, tokenIndex
								if c := buffer[position]; c < rune('a') || c > rune('f') {
									goto l531
								}
								position++
								goto l530
							l531:
								position, tokenIndex = position530, tokenIndex530
								if c := buffer[position]; c < rune('A') || c > rune('F') {
									goto l521
								}
								position++
							}
						l530:
						}
					l527:
						goto l520
					l521:
						position, tokenIndex = position521, tokenIndex521
					}
					goto l507
				l517:
					position, tokenIndex = position507, tokenIndex507
					if c := buffer[position]; c < rune('0') || c > rune('9') {
						goto l501
					}
					position++
				l532:
					{
						position533, tokenIndex533 := position, tokenIndex
						if c := buffer[position]; c < rune('0') || c > rune('9') {
							goto l533
						}
						position++
						goto l532
					l533:
						position, tokenIndex = position533, tokenIndex533
					}
				}
			l507:
				add(ruleOffset, position502)
			}
			return true
		l501:
			position, tokenIndex = position501, tokenIndex501
			return false
		},
		/* 38 Section <- <([a-z] / [A-Z] / '@')+> */
		func() bool {
			position534, tokenIndex534 := position, tokenIndex
			{
				position535 := position
				{
					position538, tokenIndex538 := position, tokenIndex
					if c := buffer[position]; c < rune('a') || c > rune('z') {
						goto l539
					}
					position++
					goto l538
				l539:
					position, tokenIndex = position538, tokenIndex538
					if c := buffer[position]; c < rune('A') || c > rune('Z') {
						goto l540
					}
					position++
					goto l538
				l540:
					position, tokenIndex = position538, tokenIndex538
					if buffer[position] != rune('@') {
						goto l534
					}
					position++
				}
			l538:
			l536:
				{
					position537, tokenIndex537 := position, tokenIndex
					{
						position541, tokenIndex541 := position, tokenIndex
						if c := buffer[position]; c < rune('a') || c > rune('z') {
							goto l542
						}
						position++
						goto l541
					l542:
						position, tokenIndex = position541, tokenIndex541
						if c := buffer[position]; c < rune('A') || c > rune('Z') {
							goto l543
						}
						position++
						goto l541
					l543:
						position, tokenIndex = position541, tokenIndex541
						if buffer[position] != rune('@') {
							goto l537
						}
						position++
					}
				l541:
					goto l536
				l537:
					position, tokenIndex = position537, tokenIndex537
				}
				add(ruleSection, position535)
			}
			return true
		l534:
			position, tokenIndex = position534, tokenIndex534
			return false
		},
		/* 39 SegmentRegister <- <('%' ([c-g] / 's') ('s' ':'))> */
		func() bool {
			position544, tokenIndex544 := position, tokenIndex
			{
				position545 := position
				if buffer[position] != rune('%') {
					goto l544
				}
				position++
				{
					position546, tokenIndex546 := position, tokenIndex
					if c := buffer[position]; c < rune('c') || c > rune('g') {
						goto l547
					}
					position++
					goto l546
				l547:
					position, tokenIndex = position546, tokenIndex546
					if buffer[position] != rune('s') {
						goto l544
					}
					position++
				}
			l546:
				if buffer[position] != rune('s') {
					goto l544
				}
				position++
				if buffer[position] != rune(':') {
					goto l544
				}
				position++
				add(ruleSegmentRegister, position545)
			}
			return true
		l544:
			position, tokenIndex = position544, tokenIndex544
			return false
		},
	}
	p.rules = _rules
}
