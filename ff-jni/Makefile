JAVA_HOME ?= $(shell dirname $$(dirname $$(readlink -f $$(which javac))))
LIBDIR := src/main/resources/io/vacco/ff
OUT := out

FG_SRC := src/fg/fg_proc.c src/fg/fg_tap.c src/fg/fg_raw.c

all: $(OUT)/fg_jni.so $(OUT)/fg_vmm

$(OUT):
	mkdir -p $(OUT)

$(OUT)/fg_jni.so: $(FG_SRC) src/jni/fg_jni.c $(FG_SRC:.c=.h) | $(OUT)
	cc -Wall -O2 -fPIC -shared \
	  -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux \
	  -Isrc/fg \
	  -o $@ src/jni/fg_jni.c $(FG_SRC)

$(OUT)/fg_vmm: src/vmm/fg_vmm.c src/vmm/include/libkrun.h src/vmm/include/libkrun_init.h | $(OUT)
	cc -Wall -O2 -Isrc/vmm/include \
	  -o $@ src/vmm/fg_vmm.c \
	  -L$(LIBDIR) -l:libkrun.so.2 -l:libkrun_init.so

clean:
	rm -rf $(OUT)
