CC = zig cc
CFLAGS =	-Wall

$(shell mkdir -p ./out)

all: compile

compile:
	$(CC) $(CFLAGS) -c ./src/main/c/ffrt.c -o ./out/ffrt.o

clean:
	rm -rf ./out
