patch -R bootrom/freedom-u540-c000-bootloader/lib/memcpy.c BA_Patch_files/AES_impl.patch
patch -R bootrom/freedom-u540-c000-bootloader/fsbl/main.c BA_Patch_files/AES_impl_2.patch
patch -R bootrom/freedom-u540-c000-bootloader/uart/uart.c BA_Patch_files/AES_impl_3.patch
patch -R bootrom/freedom-u540-c000-bootloader/uart/uart.h BA_Patch_files/AES_impl_4.patch
sudo mv bootrom/freedom-u540-c000-bootloader/lib/aes/ aes/