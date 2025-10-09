CC = zig cc
CFLAGS = -Wall -Os \
	-I./include -g -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux \
	-fno-stack-protector -fno-unwind-tables -fno-asynchronous-unwind-tables \
	-ffunction-sections -fdata-sections

$(shell mkdir -p ./out)

all: compile jni

compile:
	$(CC) $(CFLAGS) -c ./src/fg/fg_tap.c -o ./out/fg_tap.o
	$(CC) $(CFLAGS) -c ./src/fg/fg_raw.c -o ./out/fg_raw.o
	$(CC) $(CFLAGS) -c ./src/fg/fg_proc.c -o ./out/fg_proc.o
	$(CC) $(CFLAGS) -c ./src/fg/fg_unix.c -o ./out/fg_unix.o
	$(CC) $(CFLAGS) -c ./src/fg/fg_vsock.c -o ./out/fg_vsock.o

jni:
	$(CC) $(CFLAGS) -fPIC -c ./src/jni/fg_jni.c -o ./out/fg_jni.o
	$(CC) -shared -o ./fg_jni.so \
		./out/fg_jni.o ./out/fg_tap.o ./out/fg_raw.o ./out/fg_proc.o ./out/fg_unix.o ./out/fg_vsock.o

tests: all
	$(CC) $(CFLAGS) -c ./src/test/fg_tap_test.c -o ./out/fg_tap_test.o
	$(CC) -o ./out/fg_test \
		./out/fg_tap.o ./out/fg_tap_test.o
		sudo setcap 'cap_net_admin,cap_net_bind_service,cap_net_raw+eip' ./out/fg_test

clean:
	rm -rf ./out