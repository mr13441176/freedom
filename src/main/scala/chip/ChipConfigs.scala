package uec.freedom.u500

import freechips.rocketchip.config._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.tile._
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._

// Default Config
class ChipDefaultConfig extends Config(
  new WithJtagDTM            ++
  new WithNMemoryChannels(1) ++
//  new WithNBigCores(2)       ++
  new BaseConfig
)

// Chip Peripherals
class ChipPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(
    UARTParams(address = BigInt(0x64000000L)))
  case PeripherySPIKey => List(
    SPIParams(rAddress = BigInt(0x64001000L)))
  case PeripheryGPIOKey => List(
    GPIOParams(address = BigInt(0x64002000L), width = 4), //leds
    GPIOParams(address = BigInt(0x64003000L), width = 8)) //switches
  case PeripheryMaskROMKey => List(
    MaskROMParams(address = 0x78000000, name = "BootROM"))
  case ExtMem => Some(MemoryPortParams(MasterPortParams(
    base = x"8000_0000",
    size = x"4000_0000",  // 1GB
    beatBytes = site(MemoryBusKey).beatBytes,
    idBits = 4), 1))

// instead of calling "new WithNBigCores(2) ++"
// using this to modify the content of cpu: modify the cache size
  case RocketTilesKey => {
    val big = RocketTileParams(
      core   = RocketCoreParams(mulDiv = Some(MulDivParams(
        mulUnroll = 8,
        mulEarlyOut = true,
        divEarlyOut = true))),
      // Cache size = nSets * nWays * CacheBlockBytes
      // nSets = (default) 64;
      // nWays = (default) 4;
      // CacheBlockBytes = (default) 64;
      // => default cache size = 64 * 4 * 64 = 16KBytes
      dcache = Some(DCacheParams(
        // => dcache size = 32 * 2 * 64 = 4KBytes
        nSets = 32,
        nWays = 2,
        rowBits = site(SystemBusKey).beatBits,
        nMSHRs = 0,
        blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
        // => icache size = 32 * 2 * 64 = 4KBytes
        nSets = 32,
        nWays = 2,
        rowBits = site(SystemBusKey).beatBits,
        blockBytes = site(CacheBlockBytes))))
    List.tabulate(2)(i => big.copy(hartId = i))
  }
})

// Chip Configs
class ChipConfig extends Config(
  new WithNExtTopInterrupts(0)   ++
  new ChipPeripherals ++
  new ChipDefaultConfig().alter((site,here,up) => {
    case SystemBusKey => up(SystemBusKey).copy(
      errorDevice = Some(DevNullParams(
        Seq(AddressSet(0x3000, 0xfff)),
        maxAtomic=site(XLen)/8,
        maxTransfer=128,
        region = RegionType.TRACKED)))
    case PeripheryBusKey => up(PeripheryBusKey, site).copy(frequency =
      BigDecimal(site(ChipFrequencyKey)*1000000).setScale(0, BigDecimal.RoundingMode.HALF_UP).toBigInt,
      errorDevice = None)
    case DTSTimebase => BigInt(1000000)
    case JtagDTMKey => new JtagDTMConfig (
      idcodeVersion = 2,      // 1 was legacy (FE310-G000, Acai).
      idcodePartNum = 0x000,  // Decided to simplify.
      idcodeManufId = 0x489,  // As Assigned by JEDEC to SiFive. Only used in wrappers / test harnesses.
      debugIdleCycles = 5)    // Reasonable guess for synchronization
  })
)
