package uec.freedom.u500

import chisel3._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import sifive.fpgashells.clocks._
import sifive.fpgashells.shell._

class SysClockChipOverlay(val shell: ChipShell, val name: String, params: ClockInputOverlayParams)
  extends IOOverlay[Clock, ClockSourceNode]
{
  implicit val p = params.p

  def ioFactory = Input(new Clock)
  def designOutput = node

  shell { InModuleBody {
    val (c, _) = node.out(0)
    c.clock := io
    c.reset := shell.SysReset
  } }

  val node = shell { ClockSourceNode(freqMHz = 200, jitterPS = 50)(ValName(name)) }
}

class SDIOChipOverlay(val shell: ChipShell, val name: String, params: SDIOOverlayParams)
  extends SDIOOverlay(params)

class UARTChipOverlay(val shell: ChipShell, val name: String, params: UARTOverlayParams)
  extends UARTOverlay(params)

class LEDChipOverlay(val shell: ChipShell, val name: String, params: GPIOLedOverlayParams)
  extends GPIOLedOverlay(params)

class SwitchChipOverlay(val shell: ChipShell, val name: String, params: GPIOSwitchOverlayParams)
  extends GPIOSwitchOverlay(params)

class JTAGDebugChipOverlay(val shell: ChipShell, val name: String, params: JTAGDebugOverlayParams)
  extends JTAGDebugOverlay(params)

class AXI4AsMemChipOverlay(val shell: ChipShell, val name: String, params: AXI4AsMemOverlayParams)
  extends AXI4AsMemOverlay(params)

class ChipShell()(implicit p: Parameters) extends IOShell
{
  //sdc here is just because of historical reason. It does nothing
  val sdc = new SDC("shell.sdc")

  val SysReset = InModuleBody { Wire(Bool()) }

  // Order matters; ddr depends on sys_clock
  val tlclk   = Overlay(ClockInputOverlayKey)(new SysClockChipOverlay (_, _, _))
  val led     = Overlay(GPIOLedOverlayKey)   (new LEDChipOverlay      (_, _, _))
  val switch  = Overlay(GPIOSwitchOverlayKey)(new SwitchChipOverlay   (_, _, _))
  val axi4out = Overlay(AXI4AsMemOverlayKey) (new AXI4AsMemChipOverlay(_, _, _))
  val uart    = Overlay(UARTOverlayKey)      (new UARTChipOverlay     (_, _, _))
  val sdio    = Overlay(SDIOOverlayKey)      (new SDIOChipOverlay     (_, _, _))
  val jtag    = Overlay(JTAGDebugOverlayKey) (new JTAGDebugChipOverlay(_, _, _))

  val topDesign = LazyModule(p(DesignKey)(designParameters))

  // Place the sys_clock at the Shell if the user didn't ask for it
  p(ClockInputOverlayKey).foreach(_(ClockInputOverlayParams()))

  override lazy val module = new LazyRawModuleImp(this) {
    val reset = IO(Input(Bool()))
    SysReset := reset
  }
}
