# change these values if you need to
SWIG		=	swig	# apt-get install swig !
GCC		=	gcc

CC_FLAGS	=	-c -fPIC
LD_FLAGS	=	-shared -L../.. -ldisasm

BASE_NAME	=	x86disasm

export INTERFACE_FILE BASE_NAME SWIG GCC CC_FLAGS LD_FLAGS

#====================================================
# TARGETS

all: swig
dummy: swig swig-python swig-ruby swig-perl swig-tcl install uninstall clean

swig: swig-python swig-perl
# swig-rub swig-tcl

swig-python: 
	cd python && make -f Makefile-swig

swig-ruby:
	cd ruby && make -f Makefile-swig

swig-perl:
	cd perl && make -f Makefile-swig

swig-tcl:
	cd tcl && make -f Makefile-swig

# ==================================================================
install: install-python install-perl
# install-ruby install-tcl

install-python:
	cd python && sudo make -f Makefile-swig install

install-ruby:
	cd ruby && sudo make -f Makefile-swig install

install-perl:
	cd perl && sudo make -f Makefile-swig install

install-tcl:
	cd tcl && sudo make -f Makefile-swig install

# ==================================================================
uninstall: uninstall-python
#uninstall-ruby uninstall-perl uninstall-tcl

uninstall-python:
	cd python && sudo make -f Makefile-swig uninstall

uninstall-ruby:
	cd ruby && sudo make -f Makefile-swig uninstall

uninstall-perl:
	cd perl && sudo make -f Makefile-swig uninstall

uninstall-tcl:
	cd tcl && sudo make -f Makefile-swig uninstall

# ==================================================================
clean:
	cd python && make -f Makefile-swig clean
	cd ruby && make -f Makefile-swig clean
	cd perl && make -f Makefile-swig clean
	cd tcl && make -f Makefile-swig clean
