git -c submodule.rocket-chip.update=none submodule update --init --recursive
cd rocket-chip/
git -c submodule.riscv-tools.update=none submodule update --init --recursive
cd ../fpga-shells/
patch -p1 < ../fpga-shells.patch
cd ../
patch sifive-blocks/src/main/scala/devices/uart/UART.scala BA_Patch_files/UART_scala.patch
patch sifive-blocks/src/main/scala/devices/uart/UARTCtrlRegs.scala BA_Patch_files/UARTCtrlRegs_scala.patch
patch bootrom/freedom-u540-c000-bootloader/fsbl/main.c BA_Patch_files/fsblmain.patch
patch bootrom/freedom-u540-c000-bootloader/sifive/devices/uart.h BA_Patch_files/sifive_devices_uart_h.patch