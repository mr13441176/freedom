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
