package uec.keystone.nedochip

import chisel3._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.system._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import sifive.blocks.devices.pinctrl.{BasePin, EnhancedPin, EnhancedPinCtrl, Pin, PinCtrl}
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.jtag._
import uec.rocketchip.subsystem._

class NEDOSystem(implicit p: Parameters) extends RocketSubsystem
    with HasPeripheryMaskROMSlave
    with HasPeripheryDebug
    with HasPeripheryUART
    with HasPeripherySPI
    with HasPeripherySPIFlash
    with HasPeripheryGPIO
//    with HasPeripheryI2C
//    with CanHaveMasterAXI4MemPort
    with CanHaveMasterTLMemPort
{
  override lazy val module = new NEDOSystemModule(this)
}

class NEDOSystemModule[+L <: NEDOSystem](_outer: L)
  extends RocketSubsystemModuleImp(_outer)
    with HasPeripheryDebugModuleImp
    with HasPeripheryUARTModuleImp
    with HasPeripherySPIModuleImp
    with HasPeripherySPIFlashModuleImp
    with HasPeripheryGPIOModuleImp
//    with HasPeripheryI2CModuleImp
//    with CanHaveMasterAXI4MemPortModuleImp
    with CanHaveMasterTLMemPortModuleImp
{
  // Reset vector is set to the location of the mask rom
  val maskROMParams = p(PeripheryMaskROMKey)
  global_reset_vector := maskROMParams(0).address.U
}

object PinGen {
  def apply(): BasePin =  {
    val pin = new BasePin()
    pin
  }
}

//-------------------------------------------------------------------------
// E300ArtyDevKitPlatformIO
//-------------------------------------------------------------------------

class NEDOPlatformIO[T <: Data](gen: () => T)(implicit val p: Parameters) extends Bundle {
  val pins = new Bundle {
    val jtag = new JTAGPins(() => PinGen(), false)
    val gpio = new GPIOPins(() => PinGen(), p(PeripheryGPIOKey)(0))
    val qspi = new SPIPins(() => PinGen(), p(PeripherySPIFlashKey)(0))
    val uart = new UARTPins(() => PinGen())
    //val i2c = new I2CPins(() => PinGen())
    val spi = new SPIPins(() => PinGen(), p(PeripherySPIKey)(0))
  }
  val jtag_reset = Input(Bool())
  val tlport = gen()
  /*val device = new MemoryDevice
  val node = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    slaves = Seq(AXI4SlaveParameters(
      address       = Seq(AddressSet(x"8_0000_0000", x"0_FFFF_FFFF")),
      resources     = device.reg,
      regionType    = RegionType.UNCACHED,
      executable    = true,
      supportsWrite = TransferSizes(1, 128),
      supportsRead  = TransferSizes(1, 128))),
    beatBytes = 8)))*/
}


class NEDOPlatform(implicit val p: Parameters) extends Module {
  val sys = Module(LazyModule(new NEDOSystem).module)

  // Not actually sure if "sys.outer.memTLNode.head.in.head._1.params" is the
  // correct way to get the params... TODO: Get the correct way
  val io = IO(new NEDOPlatformIO(() => sys.mem_tl.head.head.cloneType) )

  // Add in debug-controlled reset.
  sys.reset := ResetCatchAndSync(clock, reset.toBool, 20)

  // The AXI4 memory port. This is a configurable one for the address space
  // and the ports are exposed inside the "foreach". Do not worry, there is
  // only one memory (unless you configure multiple memories).
  /*sys.mem_axi4.foreach{ case i =>
    i.foreach{ case io: AXI4Bundle =>
    }
  }*/

  // The TL memory port. This is a configurable one for the address space
  // and the ports are exposed inside the "foreach". Do not worry, there is
  // only one memory (unless you configure multiple memories).
  sys.mem_tl.foreach{ case i =>
    i.foreach{ case ioi:TLBundle =>
      // Connect outside
      io.tlport <> ioi
    }
  }

  //-----------------------------------------------------------------------
  // Check for unsupported rocket-chip connections
  //-----------------------------------------------------------------------

  require (p(NExtTopInterrupts) == 0, "No Top-level interrupts supported");

  // I2C
  //I2CPinsFromPort(io.pins.i2c, sys.i2c(0), clock = sys.clock, reset = sys.reset.toBool, syncStages = 0)

  // UART0
  UARTPinsFromPort(io.pins.uart, sys.uart(0), clock = sys.clock, reset = sys.reset.toBool, syncStages = 0)

  //-----------------------------------------------------------------------
  // Drive actual Pads
  //-----------------------------------------------------------------------

  // Result of Pin Mux
  GPIOPinsFromPort(io.pins.gpio, sys.gpio(0))

  // Dedicated SPI Pads
  SPIPinsFromPort(io.pins.qspi, sys.qspi(0), clock = sys.clock, reset = sys.reset.toBool, syncStages = 3)
  SPIPinsFromPort(io.pins.spi, sys.spi(0), clock = sys.clock, reset = sys.reset.toBool, syncStages = 0)

  // JTAG Debug Interface
  val sjtag = sys.debug.systemjtag.get
  JTAGPinsFromPort(io.pins.jtag, sjtag.jtag)
  sjtag.reset := io.jtag_reset
  sjtag.mfr_id := p(JtagDTMKey).idcodeManufId.U(11.W)
}
