use ExtUtils::MakeMaker;

WriteMakefile(
	'NAME'	=> 'x86disasm',
	'LIBS'	=> ['-ldisasm'],
	'OBJECT'	=> 'x86disasm_wrap.o'
);
