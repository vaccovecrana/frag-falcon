CC = zig cc
CFLAGS = -Wall -Os -fno-stack-protector -fno-unwind-tables -fno-asynchronous-unwind-tables -ffunction-sections -fdata-sections
LDFLAGS = -s -Wl,--gc-sections

$(shell mkdir -p ./out)

all: compile

compile:
	$(CC) $(CFLAGS) ./src/main/c/ffrt.c -o ./src/main/c/ffrt $(LDFLAGS) -target x86_64-linux-musl -static

clean:
	rm -rf ./out