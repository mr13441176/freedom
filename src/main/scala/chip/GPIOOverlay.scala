package uec.nedo.chip

import Chisel.Vec
import chisel3._
import chisel3.core.CompileOptions
import chisel3.util.Cat
import sifive.fpgashells.shell._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import sifive.blocks.devices.gpio._
import freechips.rocketchip.subsystem.{BaseSubsystem, PeripheryBus, PeripheryBusKey}
import freechips.rocketchip.tilelink.TLBusWrapper
import freechips.rocketchip.interrupts.IntInwardNode
import sifive.blocks.devices.pinctrl.EnhancedPin

case class GPIOLedOverlayParams(gpioParams: GPIOParams, controlBus: TLBusWrapper, intNode: IntInwardNode)(implicit val p: Parameters)
case object GPIOLedOverlayKey extends Field[Seq[DesignOverlay[GPIOLedOverlayParams, ModuleValue[UInt]]]](Nil)

case class GPIOSwitchOverlayParams(gpioParams: GPIOParams, controlBus: TLBusWrapper, intNode: IntInwardNode)(implicit val p: Parameters)
case object GPIOSwitchOverlayKey extends Field[Seq[DesignOverlay[GPIOSwitchOverlayParams, ModuleValue[UInt]]]](Nil)

abstract class GPIOLedOverlay(
  val params: GPIOLedOverlayParams)
    extends IOOverlay[UInt, ModuleValue[UInt]]
{
  implicit val p = params.p

  def width: Int

  def ioFactory = Output(UInt(width.W))

  val tlLed = GPIO.attach(GPIOAttachParams(params.gpioParams, params.controlBus, params.intNode))
  val tlLedSink = tlLed.ioNode.makeSink()

  val ledSource = BundleBridgeSource(() => UInt(width.W))
  val ledSink = shell { ledSource.makeSink() }
  val designOutput = InModuleBody { ledSource.out(0)._1 }

  InModuleBody {
    val pins = Wire(new GPIOPortIO(params.gpioParams))
    pins := tlLedSink.bundle
    val cat = Cat(Seq.tabulate(width) { i => pins.pins(width-1-i).o.oval })
    ledSource.bundle := cat
  }

  shell { InModuleBody {
    io <> ledSink.bundle
  } }
}

abstract class GPIOSwitchOverlay(
  val params: GPIOSwitchOverlayParams)
    extends IOOverlay[UInt, ModuleValue[UInt]]
{
  implicit val p = params.p

  def width: Int
  def ioFactory = Input(UInt(width.W))

  val tlSwitch = GPIO.attach(GPIOAttachParams(params.gpioParams, params.controlBus, params.intNode))
  val tlSwitchSink = tlSwitch.ioNode.makeSink()

  val switchSource = shell { BundleBridgeSource(() => UInt(width.W)) }
  val switchSink = switchSource.makeSink()
  val designOutput = InModuleBody { switchSink.bundle }

  InModuleBody {
    val pins = Wire(new GPIOPortIO(params.gpioParams))
    tlSwitchSink.bundle := pins
    (pins.pins zip switchSink.bundle.toBools).foreach { case (i, o) =>
      i.i.ival := o
      i.o.oval := false.B
      i.o.ds := false.B
      i.o.pue := false.B
      i.o.ie := false.B
      i.o.oe := false.B
    }
    //pins.pins.asUInt() := switchSource.bundle

    shell { InModuleBody {
      switchSource.bundle <> io
    } }
  }
}
