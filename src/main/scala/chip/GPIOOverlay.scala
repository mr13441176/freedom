package uec.nedo.chip

import Chisel.Vec
import chisel3._
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

abstract class GPIOLedOverlay(
  val params: GPIOLedOverlayParams)
    extends IOOverlay[UInt, ModuleValue[UInt]]
{
  implicit val p = params.p

  def width: Int

  def ioFactory = Output(UInt(width.W))
  //def ioFactory = new GPIOPortIO(params.gpioParams)

  val tlLed = GPIO.attach(GPIOAttachParams(params.gpioParams, params.controlBus, params.intNode))
  val tlLedSink = tlLed.ioNode.makeSink()

  val ledSource = BundleBridgeSource(() => UInt(width.W))
  //val ledSource = BundleBridgeSource(() => new GPIOPortIO((params.gpioParams)))
  val ledSink = shell { ledSource.makeSink() }
  val designOutput = InModuleBody { ledSource.out(0)._1 }
  //val designOutput = tlLed

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
