package uec.freedom.u500

import chisel3.core._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import sifive.fpgashells.shell._

case class AXI4AsMemOverlayParams(axiparam: AXI4BundleParameters, cacheBlockBytes: Int)(implicit val p: Parameters)
case object AXI4AsMemOverlayKey extends Field[Seq[DesignOverlay[AXI4AsMemOverlayParams, ModuleValue[AXI4Bundle]]]](Nil)

abstract class AXI4AsMemOverlay(
  val params: AXI4AsMemOverlayParams)
    extends IOOverlay[AXI4Bundle, ModuleValue[AXI4Bundle]]
{
  implicit val p = params.p
  def ioFactory = new AXI4Bundle(params.axiparam)

  val AXI4Source = BundleBridgeSource(() => new AXI4Bundle(params.axiparam))
  val AXI4Sink = shell { AXI4Source.makeSink() }
  shell {  }
  val designOutput = InModuleBody { AXI4Source.bundle }
}

