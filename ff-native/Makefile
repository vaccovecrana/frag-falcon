CC = zig cc
CFLAGS = -Wall -Os \
	-I./include -g -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux \
	-fno-stack-protector -fno-unwind-tables -fno-asynchronous-unwind-tables \
	-ffunction-sections -fdata-sections
LDFLAGS = -s -Wl,--gc-sections

OUT_DIR = ./build/native

$(shell mkdir -p ./build/native)

all: init native jni

init:
	$(CC) $(CFLAGS) ./src/main/c/init/fg_init.c -o ./ffrt $(LDFLAGS) -target x86_64-linux-musl -static

native:
	$(CC) $(CFLAGS) -c ./src/main/c/fg/fg_tap.c   -o $(OUT_DIR)/fg_tap.o
	$(CC) $(CFLAGS) -c ./src/main/c/fg/fg_raw.c   -o $(OUT_DIR)/fg_raw.o
	$(CC) $(CFLAGS) -c ./src/main/c/fg/fg_proc.c  -o $(OUT_DIR)/fg_proc.o
	$(CC) $(CFLAGS) -c ./src/main/c/fg/fg_unix.c  -o $(OUT_DIR)/fg_unix.o
	$(CC) $(CFLAGS) -c ./src/main/c/fg/fg_vsock.c -o $(OUT_DIR)/fg_vsock.o

jni:
	$(CC) $(CFLAGS) -fPIC -c ./src/main/c/jni/fg_jni.c -o $(OUT_DIR)/fg_jni.o
	$(CC) -shared -o ./fg_jni.so \
		$(OUT_DIR)/fg_jni.o $(OUT_DIR)/fg_tap.o $(OUT_DIR)/fg_raw.o \
		$(OUT_DIR)/fg_proc.o $(OUT_DIR)/fg_unix.o $(OUT_DIR)/fg_vsock.o

tests: all
	$(CC) $(CFLAGS) -c ./src/test/c/fg_tap_test.c -o $(OUT_DIR)/fg_tap_test.o
	$(CC) -o $(OUT_DIR)/fg_test \
		$(OUT_DIR)/fg_tap.o $(OUT_DIR)/fg_tap_test.o
		sudo setcap 'cap_net_admin,cap_net_bind_service,cap_net_raw+eip' $(OUT_DIR)/fg_test

clean:
	rm -rf $(OUT_DIR)
