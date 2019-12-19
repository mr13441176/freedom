git -c submodule.rocket-chip.update=none submodule update --init --recursive
cd rocket-chip/
git -c submodule.riscv-tools.update=none submodule update --init --recursive
cd ../fpga-shells/
patch -p1 < ../fpga-shells.patch
cd ../
patch sifive-blocks/src/main/scala/devices/uart/UART.scala UART_scala.patch
patch sifive-blocks/src/main/scala/devices/uart/UARTCtrlRegs.scala UARTCtrlRegs_scala.path