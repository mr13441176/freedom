#!/bin/bash
# Make the hardware (Supported for debug as well)
make
make debug
# Compile the bootloader (just a dummy bootloader)
INCLUDE="-I./bootloader -I./bootloader/sha3 -I./bootloader/ed25519"
CFLAGS="-march=rv64imafdc -mabi=lp64d -mcmodel=medany"
LDFLAGS="-march=rv64imafdc -mabi=lp64d -Os -nostartfiles -nostdlib -Wl,-Bstatic,-T,./bootloader/ram.lds,-Map,./bootloader/boot.map,--strip-debug"
riscv64-unknown-elf-gcc $INCLUDE -c ./bootloader/start.S -o ./bootloader/start.o $CFLAGS
riscv64-unknown-elf-gcc $INCLUDE -c ./bootloader/main.c -o ./bootloader/main.o $CFLAGS
riscv64-unknown-elf-gcc $INCLUDE -c ./bootloader/sha3/sha3.c -o ./bootloader/sha3/sha3.o $CFLAGS
riscv64-unknown-elf-gcc $INCLUDE -c ./bootloader/ed25519/keypair.c -o ./bootloader/ed25519/keypair.o $CFLAGS
riscv64-unknown-elf-gcc $INCLUDE -c ./bootloader/ed25519/sc.c -o ./bootloader/ed25519/sc.o $CFLAGS
riscv64-unknown-elf-gcc $INCLUDE -c ./bootloader/ed25519/sign.c -o ./bootloader/ed25519/sign.o $CFLAGS
riscv64-unknown-elf-gcc $INCLUDE -c ./bootloader/ed25519/fe.c -o ./bootloader/ed25519/fe.o $CFLAGS
riscv64-unknown-elf-gcc $INCLUDE -c ./bootloader/ed25519/ge.c -o ./bootloader/ed25519/ge.o $CFLAGS
riscv64-unknown-elf-gcc $INCLUDE -c ./bootloader/ed25519/verify.c -o ./bootloader/ed25519/verify.o $CFLAGS
riscv64-unknown-elf-gcc $INCLUDE -c ./bootloader/memcpy.c -o ./bootloader/memcpy.o $CFLAGS
riscv64-unknown-elf-gcc $INCLUDE -c ./bootloader/strlen.c -o ./bootloader/strlen.o $CFLAGS
riscv64-unknown-elf-gcc ./bootloader/start.o ./bootloader/main.o ./bootloader/sha3/sha3.o ./bootloader/memcpy.o ./bootloader/strlen.o ./bootloader/ed25519/keypair.o ./bootloader/ed25519/sc.o ./bootloader/ed25519/sign.o ./bootloader/ed25519/fe.o ./bootloader/ed25519/ge.o ./bootloader/ed25519/verify.o -o ./bootloader/boot.elf $LDFLAGS
# Just dump the assembly, for fun
riscv64-unknown-elf-objdump -D ./bootloader/boot.elf -M no-aliases,numeric > ./bootloader/boot.noaliases.dump
riscv64-unknown-elf-objdump -D ./bootloader/boot.elf > ./bootloader/boot.dump
# Get the binary machine code
riscv64-unknown-elf-objcopy -O binary ./bootloader/boot.elf ./bootloader/boot.bin
# Get the silly hex file for verilog
#od -t x4 -An -w4 -v ./bootloader/boot.bin > boot.hex

