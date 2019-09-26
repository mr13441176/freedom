package uec.nedo.chip

import chisel3._
import chisel3.experimental.{attach, IO, withClockAndReset}
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.SyncResetSynchronizerShiftReg
import sifive.fpgashells.clocks._
import sifive.fpgashells.shell._
import sifive.fpgashells.ip.xilinx._
import sifive.blocks.devices.chiplink._
import sifive.fpgashells.devices.xilinx.xilinxvc707mig._
import sifive.fpgashells.devices.xilinx.xilinxvc707pciex1._

abstract class ClockInputOverlay(
  val params: ClockInputOverlayParams)
    extends IOOverlay[Clock, ClockSourceNode]
{
  implicit val p = params.p
  def node: ClockSourceNode

  def ioFactory = Input(new Clock)
  def designOutput = node

  val clock = shell { InModuleBody {
    val (clk, edge) = node.out.head
    clk.clock
  } }
}

class SysClockChipOverlay(val shell: ChipShell, val name: String, params: ClockInputOverlayParams)
  extends ClockInputOverlay(params)
{
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
{
  val width = 8

  /*shell { InModuleBody {
    io := ledSink.bundle
  } }*/
}

class SwitchChipOverlay(val shell: ChipShell, val name: String, params: GPIOSwitchOverlayParams)
  extends GPIOSwitchOverlay(params)
{
  val width = 8

  /*shell { InModuleBody {
    switchSource.bundle := io
  } }*/
}

class JTAGDebugChipOverlay(val shell: ChipShell, val name: String, params: JTAGDebugOverlayParams)
  extends JTAGDebugOverlay(params)
{
  shell { InModuleBody {
    shell.sdc.addClock("JTCK", IOPin(io.jtag_TCK), 10)
    shell.sdc.addGroup(clocks = Seq("JTCK"))
    shell.xdc.clockDedicatedRouteFalse(IOPin(io.jtag_TCK))
    val packagePinsWithPackageIOs = Seq(("R32", IOPin(io.jtag_TCK)),
                                        ("W36", IOPin(io.jtag_TMS)),
                                        ("W37", IOPin(io.jtag_TDI)),
                                        ("V40", IOPin(io.jtag_TDO)))

    packagePinsWithPackageIOs foreach { case (pin, io) => {
      shell.xdc.addPackagePin(io, pin)
      shell.xdc.addIOStandard(io, "LVCMOS18")
      shell.xdc.addPullup(io)
    } }
  } }
}

case object ChipDDRSize extends Field[BigInt](0x40000000L * 1) // 1GB
/*class DDRChipOverlay(val shell: ChipShell, val name: String, params: DDROverlayParams)
  extends DDROverlay[XilinxVC707MIGPads](params)
{
  val size = p(ChipDDRSize)

  val migParams = XilinxVC707MIGParams(address = AddressSet.misaligned(params.baseAddress, size))
  val mig = LazyModule(new XilinxVC707MIG(migParams))
  val ioNode = BundleBridgeSource(() => mig.module.io.cloneType)
  val topIONode = shell { ioNode.makeSink() }
  val ddrUI     = shell { ClockSourceNode(freqMHz = 200) }
  val areset    = shell { ClockSinkNode(Seq(ClockSinkParameters())) }
  areset := params.wrangler := ddrUI

  def designOutput = mig.node
  def ioFactory = new XilinxVC707MIGPads(size)

  InModuleBody { ioNode.bundle <> mig.module.io }

  shell { InModuleBody {
    require (shell.tlclk.isDefined, "Use of DDRChipOverlay depends on SysClockChipOverlay")
    val (sys, _) = shell.tlclk.get.node.out(0)
    val (ui, _) = ddrUI.out(0)
    val (ar, _) = areset.in(0)
    val port = topIONode.bundle.port
    io <> port
    ui.clock := port.ui_clk
    ui.reset := !port.mmcm_locked || port.ui_clk_sync_rst
    port.sys_clk_i := sys.clock.asUInt
    port.sys_rst := sys.reset
    port.aresetn := !ar.reset
  } }

  shell.sdc.addGroup(clocks = Seq("clk_pll_i"))
}*/
class AvalonAsMemOverlay(val shell: ChipShell, val name: String, params: DDROverlayParams)
  extends DDROverlay[XilinxVC707MIGPads](params)
{
  val size = p(ChipDDRSize)

  val migParams = XilinxVC707MIGParams(address = AddressSet.misaligned(params.baseAddress, size))
  val mig = LazyModule(new XilinxVC707MIG(migParams))
  val ioNode = BundleBridgeSource(() => mig.module.io.cloneType)
  val topIONode = shell { ioNode.makeSink() }
  val ddrUI     = shell { ClockSourceNode(freqMHz = 200) }
  val areset    = shell { ClockSinkNode(Seq(ClockSinkParameters())) }
  areset := params.wrangler := ddrUI

  def designOutput = mig.node
  def ioFactory = new XilinxVC707MIGPads(size)

  InModuleBody { ioNode.bundle <> mig.module.io }

  shell { InModuleBody {
    require (shell.tlclk.isDefined, "Use of DDRChipOverlay depends on SysClockChipOverlay")
    val (sys, _) = shell.tlclk.get.node.out(0)
    val (ui, _) = ddrUI.out(0)
    val (ar, _) = areset.in(0)
    val port = topIONode.bundle.port
    io <> port
    ui.clock := port.ui_clk
    ui.reset := !port.mmcm_locked || port.ui_clk_sync_rst
    port.sys_clk_i := sys.clock.asUInt
    port.sys_rst := sys.reset
    port.aresetn := !ar.reset
  } }

  shell.sdc.addGroup(clocks = Seq("clk_pll_i"))
}

class XDC(val name: String)
{
  private var constraints: Seq[() => String] = Nil
  protected def addConstraint(command: => String) { constraints = (() => command) +: constraints }
  ElaborationArtefacts.add(name, constraints.map(_()).reverse.mkString("\n") + "\n")

  def addBoardPin(io: IOPin, pin: String) {
    addConstraint(s"set_property BOARD_PIN {${pin}} ${io.sdcPin}")
  }
  def addPackagePin(io: IOPin, pin: String) {
    addConstraint(s"set_property PACKAGE_PIN {${pin}} ${io.sdcPin}")
  }
  def addIOStandard(io: IOPin, standard: String) {
    addConstraint(s"set_property IOSTANDARD {${standard}} ${io.sdcPin}")
  }
  def addPullup(io: IOPin) {
    addConstraint(s"set_property PULLUP {TRUE} ${io.sdcPin}")
  }
  def addIOB(io: IOPin) {
    if (io.isOutput) {
      addConstraint(s"set_property IOB {TRUE} [ get_cells -of_objects [ all_fanin -flat -startpoints_only ${io.sdcPin}]]")
    } else {
      addConstraint(s"set_property IOB {TRUE} [ get_cells -of_objects [ all_fanout -flat -endpoints_only ${io.sdcPin}]]")
    }
  }
  def addSlew(io: IOPin, speed: String) {
    addConstraint(s"set_property SLEW {${speed}} ${io.sdcPin}")
  }
  def addTermination(io: IOPin, kind: String) {
    addConstraint(s"set_property OFFCHIP_TERM {${kind}} ${io.sdcPin}")
  }
  def clockDedicatedRouteFalse(io: IOPin) {
    addConstraint(s"set_property CLOCK_DEDICATED_ROUTE {FALSE} [get_nets ${io.sdcPin}]")
  }
}

abstract class ChipBaseShell()(implicit p: Parameters) extends IOShell
{
  val sdc = new SDC("shell.sdc")
  val xdc = new XDC("shell.xdc")

  ElaborationArtefacts.add("shell.vivado.tcl",
    """set shell_vivado_tcl [file normalize [info script]]
      |set shell_vivado_idx [string last ".shell.vivado.tcl" $shell_vivado_tcl]
      |add_files -fileset [current_fileset -constrset] [string replace $shell_vivado_tcl $shell_vivado_idx 999 ".shell.sdc"]
      |add_files -fileset [current_fileset -constrset] [string replace $shell_vivado_tcl $shell_vivado_idx 999 ".shell.xdc"]
      |""".stripMargin)
}

class ChipShell()(implicit p: Parameters) extends ChipBaseShell
{
  val SysReset = InModuleBody { Wire(Bool()) }

  // Order matters; ddr depends on sys_clock
  val tlclk  = Overlay(ClockInputOverlayKey)(new SysClockChipOverlay (_, _, _))
  val led    = Overlay(GPIOLedOverlayKey)   (new LEDChipOverlay      (_, _, _))
  val switch = Overlay(GPIOSwitchOverlayKey)(new SwitchChipOverlay   (_, _, _))
  val ddr    = Overlay(DDROverlayKey)       (new AvalonAsMemOverlay  (_, _, _))
  val uart   = Overlay(UARTOverlayKey)      (new UARTChipOverlay     (_, _, _))
  val sdio   = Overlay(SDIOOverlayKey)      (new SDIOChipOverlay     (_, _, _))
  val jtag   = Overlay(JTAGDebugOverlayKey) (new JTAGDebugChipOverlay(_, _, _))

  val topDesign = LazyModule(p(DesignKey)(designParameters))

  // Place the sys_clock at the Shell if the user didn't ask for it
  p(ClockInputOverlayKey).foreach(_(ClockInputOverlayParams()))

  override lazy val module = new LazyRawModuleImp(this) {
    val reset = IO(Input(Bool()))

    SysReset := reset
  }
}
