package uec.freedom.u500

import chisel3._
import freechips.rocketchip.config._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.fpgashells.clocks._
import sifive.fpgashells.shell._

class ChipWrapper()(implicit p: Parameters) extends LazyModule
{
  val sysClock  = p(ClockInputOverlayKey).head.apply(ClockInputOverlayParams())
  val coreClock = ClockSinkNode(freqMHz = p(ChipFrequencyKey))
  coreClock := sysClock

  // removing the debug trait is invasive, so we hook it up externally for now
  val jt = p(JTAGDebugOverlayKey).headOption.map(_(JTAGDebugOverlayParams())).get

  val topMod = LazyModule(new ChipDesign()(p))

  override lazy val module = new LazyRawModuleImp(this) {
    val (core, _) = coreClock.in(0)
    childClock := core.clock

    val djtag = topMod.module.debug.systemjtag.get
    djtag.jtag.TCK := jt.jtag_TCK
    djtag.jtag.TMS := jt.jtag_TMS
    djtag.jtag.TDI := jt.jtag_TDI
    jt.jtag_TDO    := djtag.jtag.TDO.data

    djtag.mfr_id := p(JtagDTMKey).idcodeManufId.U(11.W)
    djtag.reset  := core.reset

    childReset := core.reset | topMod.module.debug.ndreset
  }
}

object ChipFrequencyKey extends Field[Double](100.0)

class ChipDesign()(implicit p: Parameters) extends RocketSubsystem
    with HasPeripheryMaskROMSlave
    with HasPeripheryDebug
{
  val tlclock = new FixedClockResource("tlclk", p(ChipFrequencyKey))

  // hook up UARTs, based on configuration and available overlays
  val divinit = (p(PeripheryBusKey).frequency / 115200).toInt
  (p(PeripheryUARTKey) zip p(UARTOverlayKey)).foreach { case (uparam, uoverlay) => {
    val u = uoverlay(UARTOverlayParams(uparam, divinit, pbus, ibus.fromAsync))
    tlclock.bind(u.device)
  } }

  (p(PeripherySPIKey) zip p(SDIOOverlayKey)).foreach { case (sparam, soverlay) => {
    val s = soverlay(SDIOOverlayParams(sparam, pbus, ibus.fromAsync))
    tlclock.bind(s.device)
    val mmc = new MMCDevice(s.device)
    ResourceBinding {
      Resource(mmc, "reg").bind(ResourceAddress(0))
    }
  } }

  val BusOutAsMem = p(BusAsMemOverlayKey).headOption.map(_(BusAsMemOverlayParams(p(ExtMem).get.master,cacheBlockBytes)))
  BusOutAsMem.get := mbus.toDRAMController(Some("DirectBusAsMemPort"))()

  // Work-around for a kernel bug (command-line ignored if /chosen missing)
  val chosen = new DeviceSnippet {
    def describe() = Description("chosen", Map())
  }

  // LEDs / GPIOs
  val gpioParams = p(PeripheryGPIOKey)
  p(GPIOLedOverlayKey).foreach { case goverlay =>
    val g = goverlay(GPIOLedOverlayParams(gpioParams(0), pbus, ibus.fromAsync))
  }
  p(GPIOSwitchOverlayKey).foreach { case goverlay =>
    val g = goverlay(GPIOSwitchOverlayParams(gpioParams(1), pbus, ibus.fromAsync))
  }

  override lazy val module = new ChipSystemModule(this)
}

class ChipSystemModule[+L <: ChipDesign](_outer: L)
  extends RocketSubsystemModuleImp(_outer)
    with HasRTCModuleImp
    with HasPeripheryDebugModuleImp
{
  // Reset vector is set to the location of the mask rom
  val maskROMParams = p(PeripheryMaskROMKey)
  global_reset_vector := maskROMParams(0).address.U
}

class ChipDesignTop extends Config(
  new ChipConfig().alter((site, here, up) => {
    case DesignKey => { (p:Parameters) => new ChipWrapper()(p) }
    case ChipFrequencyKey => 100.0
  }))
