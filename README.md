Modified from https://github.com/sifive/freedom to make the VC707 FPGA board to work with the KeyStone project.

### To set your environment:

	$ vi setenv.sh
	and change the correct paths in your machine. There are two paths for RISC-V gnu toolchain and Vivado.
	
	Then from this point forward, you can auto set your environment by simply:
	$ . setenv.sh

### To select the PCIE build option

Modify the parameter "export pcie := yes/no" in the makefile of Makefile.vc707-u500devkit or Makefile.vc707-u500devkit-keystone.

### To build for normal make (without KeyStone)

	$ make -f Makefile.vc707-u500devkit verilog
	$ make -f Makefile.vc707-u500devkit mcs

### To build for KeyStone make

	$ make -f Makefile.vc707-u500devkit-keystone verilog
	$ make -f Makefile.vc707-u500devkit-keystone mcs

After the KeyStone make, there are two files **vc707zsbl.hex** and **vc707fsbl.bin** are generated in the folder **bootrom/freedom-u540-c000-bootloader/**

The **vc707zsbl.hex** was already built-in in the bootrom's code of the hardware.

The **vc707fsbl.bin** is meant to be coppied to the 4th partition of the SD card:

	$ cd bootrom/freedom-u540-c000-bootloader/
	$ sudo dd if=vc707fsbl.bin of=/dev/sdX4 bs=4096 conv=fsync
	where the X4 is the 4th partition of the USB device

### If you want clean things up

	for normal make
	$ make -f Makefile.vc707-u500devkit clean
	
	for KeyStone make
	$ make -f Makefile.vc707-u500devkit-keystone clean

### Some notes

The maximum frequency for the VC707 board with the PCIE option is 125MHz, and without the PCIE option is 150MHz. Built files are under builds/vc707-u500devkit/obj/

The built verilog source code: VC707Shell.v. The two files for flash programming: VC707Shell.mcs and VC707Shell.prm. And the FPGA bitstream file for direct programming: VC707Shell.bit.

Sometime the make mcs end with timing error and not continue to generate the final mcs files for flash programming, but still, it did generated the bit file. Then, we can manually generate the .mcs from the .bit:

	$ cd builds/vc707-u500devkit/obj/
	(cd to the build folder)
	
	$ vivado -nojournal -mode batch -source ../../../fpga-shells/xilinx/common/tcl/write_cfgmem.tcl -tclargs vc707 VC707Shell.mcs VC707Shell.bit
	the VC707Shell.mcs & VC707Shell.prm will appear after this
