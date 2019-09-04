#include <string.h>
#include "sha3.h"
#include "platform.h"

typedef unsigned char byte;

// HTIF stuff

extern volatile uint64_t tohost;
extern volatile uint64_t fromhost;

void __attribute__((noreturn)) tohost_exit(uintptr_t code)
{
  tohost = (code << 1) | 1;
  while (1);
}

static uintptr_t syscall(uintptr_t which, uint64_t arg0, uint64_t arg1, uint64_t arg2)
{
  volatile uint64_t magic_mem[8] __attribute__((aligned(64)));
  magic_mem[0] = which;
  magic_mem[1] = arg0;
  magic_mem[2] = arg1;
  magic_mem[3] = arg2;
  __sync_synchronize();

  tohost = (uintptr_t)magic_mem;
  while (fromhost == 0)
    ;
  fromhost = 0;

  __sync_synchronize();
  return magic_mem[0];
}

#define SYS_write 64
void printstr(const char* s)
{
  syscall(SYS_write, 1, (uintptr_t)s, strlen(s));
}

#undef putchar
int putchar(int ch)
{
  static __thread char buf[64] __attribute__((aligned(64)));
  static __thread int buflen = 0;

  buf[buflen++] = ch;

  if (ch == '\n' || buflen == sizeof(buf))
  {
    syscall(SYS_write, 1, (uintptr_t)buf, buflen);
    buflen = 0;
  }

  return 0;
}

void printhex(uint64_t x)
{
  char str[17];
  int i;
  for (i = 0; i < 16; i++)
  {
    str[15-i] = (x & 0xF) + ((x & 0xF) < 10 ? '0' : 'a'-10);
    x >>= 4;
  }
  str[16] = 0;

  printstr(str);
}

void printhex32(uint32_t x)
{
  char str[9];
  int i;
  for (i = 0; i < 8; i++)
  {
    str[7-i] = (x & 0xF) + ((x & 0xF) < 10 ? '0' : 'a'-10);
    x >>= 4;
  }
  str[8] = 0;

  printstr(str);
}

// HW SHA-3 stuff

void hwsha3_init() {
  SHA3_REG(SHA3_REG_STATUS) = 1 << 24; // Reset, and also put 0 in size
}

void hwsha3_update(void* data, size_t size) {
  uint64_t tmp;
  byte* d = (byte*)data;
  byte* t = (byte*)&tmp;
  while(size >= 8) {
    for(int i = 0; i < 8; i++) {
      t[7-i] = d[i];
    }
    SHA3_REG64(SHA3_REG_DATA_0) = tmp;
    SHA3_REG(SHA3_REG_STATUS) = 0;
    SHA3_REG(SHA3_REG_STATUS) = 1 << 16;
    size -= 8;
    d += 8;
  }
  if(size > 0) {
    for(int i = 0; i < size; i++) {
      t[7-i] = d[i];
    }
    SHA3_REG64(SHA3_REG_DATA_0) = tmp;
    SHA3_REG(SHA3_REG_STATUS) = size & 0x7;
    SHA3_REG(SHA3_REG_STATUS) = 1 << 16;
  }
}

void hwsha3_final(byte* hash, void* data, size_t size) {
  uint64_t tmp;
  byte* d = (byte*)data;
  byte* t = (byte*)&tmp;
  while(size >= 8) {
    for(int i = 0; i < 8; i++) {
      t[7-i] = d[i];
    }
    size -= 8;
    SHA3_REG64(SHA3_REG_DATA_0) = tmp;
    SHA3_REG(SHA3_REG_STATUS) = 0;
    SHA3_REG(SHA3_REG_STATUS) = size?(1 << 16):(3 << 16);
    d += 8;
  }
  if(size > 0) {
    for(int i = 0; i < size; i++) {
      t[7-i] = d[i];
    }
    SHA3_REG64(SHA3_REG_DATA_0) = tmp;
    SHA3_REG(SHA3_REG_STATUS) = size & 0x7;
    SHA3_REG(SHA3_REG_STATUS) = 3 << 16;
  }
  while(SHA3_REG(SHA3_REG_STATUS) & (1 << 10));
  for(int i = 0; i < 16; i++) {
    *(((uint32_t*)hash) + i) = *(((uint32_t*)(SHA3_CTRL_ADDR+SHA3_REG_HASH_0)) + i);
  }
}

// Main program

int main(int argc, char** argv) {
  sha3_ctx_t hash_ctx;
  //void *uart = (void*)UART0_CTRL_ADDR;
  printstr("Hello world, FSBL\r\n");
  
  // Test the hardware with the software SHA3
  byte hash[64];
  uint32_t* hs = (uint32_t*)hash;
  sha3_init(&hash_ctx, 64);
  sha3_update(&hash_ctx, (void*)"FOX1", 4);
  sha3_final(hash, &hash_ctx);
  for(int i = 0; i < 16; i++) 
     printhex32(*(hs+i));
  printstr("\r\n");
    
  hwsha3_init(&hash_ctx);
  hwsha3_final(hash, (void*)"FOX1", 4);
  for(int i = 0; i < 16; i++) 
      printhex32(*(hs+i));
  printstr("\r\n");
  
  tohost_exit(0);
  return 0;
}

