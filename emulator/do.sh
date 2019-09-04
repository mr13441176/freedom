#!/bin/bash
# Make the hardware (Supported for debug as well)
make
make debug
# Compile the bootloader (just a dummy bootloader)
riscv64-unknown-elf-gcc -I./bootloader -I./bootloader/sha3 -c ./bootloader/start.S -o ./bootloader/start.o -march=rv64imafdc -mabi=lp64d -mcmodel=medany
riscv64-unknown-elf-gcc -I./bootloader -I./bootloader/sha3 -c ./bootloader/main.c -o ./bootloader/main.o -march=rv64imafdc -mabi=lp64d -mcmodel=medany
riscv64-unknown-elf-gcc -I./bootloader -I./bootloader/sha3 -c ./bootloader/sha3/sha3.c -o ./bootloader/sha3/sha3.o -march=rv64imafdc -mabi=lp64d -mcmodel=medany
riscv64-unknown-elf-gcc -I./bootloader -I./bootloader/sha3 -c ./bootloader/memcpy.c -o ./bootloader/memcpy.o -march=rv64imafdc -mabi=lp64d -mcmodel=medany
riscv64-unknown-elf-gcc -I./bootloader -I./bootloader/sha3 -c ./bootloader/strlen.c -o ./bootloader/strlen.o -march=rv64imafdc -mabi=lp64d -mcmodel=medany
riscv64-unknown-elf-gcc ./bootloader/start.o ./bootloader/main.o ./bootloader/sha3/sha3.o ./bootloader/memcpy.o ./bootloader/strlen.o -o ./bootloader/boot.elf -march=rv64imafdc -mabi=lp64d -Os -nostartfiles -nostdlib -Wl,-Bstatic,-T,./bootloader/ram.lds,-Map,./bootloader/boot.map,--strip-debug
# Just dump the assembly, for fun
riscv64-unknown-elf-objdump -D ./bootloader/boot.elf -M no-aliases,numeric > ./bootloader/boot.noaliases.dump
riscv64-unknown-elf-objdump -D ./bootloader/boot.elf > ./bootloader/boot.dump
# Get the binary machine code
riscv64-unknown-elf-objcopy -O binary ./bootloader/boot.elf ./bootloader/boot.bin
# Get the silly hex file for verilog
#od -t x4 -An -w4 -v ./bootloader/boot.bin > boot.hex

