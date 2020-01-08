	.text
foo:
	addis 22,2,bar@toc@ha
	ld 0,bar@toc@l(22)
