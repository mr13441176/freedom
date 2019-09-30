package uec.freedom.u500

import chisel3._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import sifive.fpgashells.clocks._
import sifive.fpgashells.shell._

case class DDROverlayParams(
  baseAddress: BigInt)(
  implicit val p: Parameters)

case object DDROverlayKey extends Field[Seq[DesignOverlay[DDROverlayParams, TLInwardNode]]](Nil)

abstract class DDROverlay[IO <: Data](val params: DDROverlayParams)
  extends IOOverlay[IO, TLInwardNode]
{
  implicit val p = params.p
}
