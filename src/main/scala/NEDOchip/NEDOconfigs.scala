// See LICENSE for license details.
package uec.keystone.nedochip

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.system._
import freechips.rocketchip.tile._

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._

// The number of gpios that we want as input
case object GPIOInKey extends Field[Int]

// Default Config
class ChipDefaultConfig extends Config(
  new WithJtagDTM            ++
  new WithNMemoryChannels(1) ++
  new WithNBigCores(2)       ++
  new BaseConfig
)

// Chip Peripherals
class ChipPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(
    UARTParams(address = BigInt(0x64000000L)))
  case PeripherySPIKey => List(
    SPIParams(rAddress = BigInt(0x64001000L)))
  case PeripheryGPIOKey => List(
    GPIOParams(address = BigInt(0x64002000L), width = 4))
  case GPIOInKey => 2
  //case PeripheryI2CKey => List(
  //  I2CParams(address = 0x64003000))
  case PeripherySPIFlashKey => List(
    SPIFlashParams(
      fAddress = 0x20000000,
      rAddress = 0x64004000,
      defaultSampleDel = 3))
  case PeripheryMaskROMKey => List(
    MaskROMParams(address = 0x78000000, name = "BootROM"))
  case ExtMem => Some(MemoryPortParams(MasterPortParams(
    base = x"8_0000_0000",
    size = x"1_0000_0000",
    beatBytes = site(MemoryBusKey).beatBytes,
    idBits = 4), 1))
})

// Chip Configs
class ChipConfig extends Config(
  new WithNExtTopInterrupts(0)   ++
  new ChipPeripherals ++
  new ChipDefaultConfig().alter((site,here,up) => {
    case DTSTimebase => BigInt(1000000)
    case JtagDTMKey => new JtagDTMConfig (
      idcodeVersion = 2,      // 1 was legacy (FE310-G000, Acai).
      idcodePartNum = 0x000,  // Decided to simplify.
      idcodeManufId = 0x489,  // As Assigned by JEDEC to SiFive. Only used in wrappers / test harnesses.
      debugIdleCycles = 5)    // Reasonable guess for synchronization
  })
)
