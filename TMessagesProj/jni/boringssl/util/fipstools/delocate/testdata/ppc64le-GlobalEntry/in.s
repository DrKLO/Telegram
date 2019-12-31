	.text
foo:
.LCF0:
0:
	addis 2,12,.TOC.-.LCF0@ha
	addi 2,2,.TOC.-.LCF0@l
	.localentry foo,.-foo
.LVL0:
	bl
