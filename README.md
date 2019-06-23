Modified from [sifive-freedom](https://github.com/sifive/freedom) to make the VC707 FPGA board to work with the KeyStone project.

# Index

- [Index](#index)
- [I. Set Environment](#i-set-environment)
- [II. To build](#ii-to-build)
  * [II. a) Without KeyStone](#ii-a-without-keystone)
  * [II. b) With KeyStone](#ii-b-with-keystone)
- [III. Notes](#iii-notes)

# I. Set environment

	$ vi setenv.sh

Then change the correct paths in your machine. There are two paths for RISC-V gnu toolchain and Vivado.	Then from this point forward, you can auto set your environment by simply:

	$ . setenv.sh

### About the PCIE build option

Modify the parameter "**export pcie := yes/no**" in the makefile of **Makefile.vc707-u500devkit** or **Makefile.vc707-u500devkit-keystone**.

# II. To build

### II. a) Without KeyStone

	$ make -f Makefile.vc707-u500devkit verilog
	$ make -f Makefile.vc707-u500devkit mcs
	
To clean things:
	
	$ make -f Makefile.vc707-u500devkit clean

### II. b) With KeyStone

	$ make -f Makefile.vc707-u500devkit-keystone verilog
	$ make -f Makefile.vc707-u500devkit-keystone mcs

After the KeyStone make, there are two files **vc707zsbl.hex** and **vc707fsbl.bin** are generated in the folder **bootrom/freedom-u540-c000-bootloader/**

The **vc707zsbl.hex** is the bootrom code of the hardware.

And the **vc707fsbl.bin** is meant to be copied to the 4th partition of the SD card:

	$ cd bootrom/freedom-u540-c000-bootloader/
	$ sudo dd if=vc707fsbl.bin of=/dev/sdX4 bs=4096 conv=fsync
	where the X4 is the 4th partition of the USB device
	
To clean things:

	$ make -f Makefile.vc707-u500devkit-keystone clean

# III. Notes

The maximum frequency for the VC707 board with the PCIE option is 125MHz, and without the PCIE option is 150MHz. Built files are under **builds/vc707-u500devkit/obj/**

The important built files are:
	
   VC707Shell.v				: the verilog source code
   VC707Shell.mcs and VC707Shell.prm	: the two files for flash programming
   VC707Shell.bit			: the bitstream file for direct programming

Sometime the make mcs end with timing error and not continue to generate the final mcs files for flash programming, but still, it did generated the bit file. Then, we can manually generate the .mcs from the .bit:

	$ cd builds/vc707-u500devkit/obj/
	(cd to the build folder)
	
	$ vivado -nojournal -mode batch -source ../../../fpga-shells/xilinx/common/tcl/write_cfgmem.tcl -tclargs vc707 VC707Shell.mcs VC707Shell.bit
	the VC707Shell.mcs & VC707Shell.prm will appear after this
