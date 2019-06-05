Modified from https://github.com/sifive/freedom to make the VC707 FPGA board to work with the KeyStone project.

To set your environment:

	$ vi setenv.sh
	and change the correct paths in your machine. There are two paths for RISC-V gnu toolchain and Vivado.
	
	Then from this point forward, you can auto set your environment by simply:
	$ . setenv.sh

To select the pcie build option: modify the parameter "export pcie := yes/no" in the makefile of Makefile.vc707-u500devkit and Makefile.vc707-u500devkit-keystone.

Then, to normal make without KeyStone:

	$ make -f Makefile.vc707-u500devkit verilog
	$ make -f Makefile.vc707-u500devkit mcs

Or to make with KeyStone:

	$ make -f Makefile.vc707-u500devkit-keystone verilog
	$ make -f Makefile.vc707-u500devkit-keystone mcs


The maximum frequency for the VC707 board with the PCIE option is 125MHz, and without the PCIE option is 150MHz. Built files are under builds/vc707-u500devkit/obj/

The built verilog source code: VC707Shell.v. The two files for flash programming: VC707Shell.mcs and VC707Shell.prm. And the FPGA bitstream file (for direct programming): VC707Shell.bit.

Sometime the make mcs end with timing error and not continue to generate the final mcs files for flash programming, but still, it do generated the bit file. Then, we can manually generate the .mcs from the .bit:

	$ cd builds/vc707-u500devkit/obj/
	(cd to the build folder)
	
	$ vivado -nojournal -mode batch -source ../../../fpga-shells/xilinx/common/tcl/write_cfgmem.tcl -tclargs vc707 VC707Shell.mcs VC707Shell.bit
	the VC707Shell.mcs & VC707Shell.prm will appear after this
