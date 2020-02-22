mv aes/ bootrom/freedom-u540-c000-bootloader/lib/aes/ 
patch bootrom/freedom-u540-c000-bootloader/lib/memcpy.c BA_Patch_files/AES_impl.patch
# no need this, duplicate with fsblmain.patch
#patch bootrom/freedom-u540-c000-bootloader/fsbl/main.c BA_Patch_files/AES_impl_2.patch
patch bootrom/freedom-u540-c000-bootloader/uart/uart.c BA_Patch_files/AES_impl_3.patch
patch bootrom/freedom-u540-c000-bootloader/uart/uart.h BA_Patch_files/AES_impl_4.patch
patch bootrom/freedom-u540-c000-bootloader/Makefile BA_Patch_files/AES_impl_5.patch
patch bootrom/freedom-u540-c000-bootloader/lib/aes/aes.c BA_Patch_files/AES_impl_6.patch
