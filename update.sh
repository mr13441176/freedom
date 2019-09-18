git -c submodule.rocket-chip.update=none submodule update --init --recursive
cd rocket-chip/
git -c submodule.riscv-tools.update=none submodule update --init --recursive
cd ../fpga-shells/
patch -p1 < ../fpga-shells.patch
cd ../bootrom/freedom-u540-c000-bootloader/
patch -p1 < ../../freedom-u540-c000-bootloader.patch
cd ../../
