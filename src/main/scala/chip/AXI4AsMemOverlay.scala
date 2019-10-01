package uec.freedom.u500

import chisel3._
import chisel3.util._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import sifive.fpgashells.shell._

case class AXI4AsMemOverlayParams(masterParam: MasterPortParams)(implicit val p: Parameters)
case object AXI4AsMemOverlayKey extends Field[Seq[DesignOverlay[AXI4AsMemOverlayParams, TLInwardNode]]](Nil)

abstract class AXI4AsMemOverlay[IO <: Data](val params: AXI4AsMemOverlayParams)
  extends IOOverlay[AXI4Bundle, TLInwardNode]
{
  implicit val p = params.p

  val AXI4OutAsMem = LazyModule(new AXI4AsMem(params.masterParam))
  val slaveParam = AXI4OutAsMem.slaveParam

  val ioNode = BundleBridgeSource(() => AXI4OutAsMem.module.io.cloneType)
  val ioNodeSink = shell { ioNode.makeSink() }

  def ioFactory = new AXI4Bundle( AXI4BundleParameters (
    log2Ceil(slaveParam.maxAddress+1),    // addrBits
    slaveParam.beatBytes * 8,             // dataBits
    log2Ceil(params.masterParam.idBits)   // idBits
  ) )
  def designOutput = AXI4OutAsMem.node

  InModuleBody {
    ioNode.bundle <> AXI4OutAsMem.module.io
  }

  shell { InModuleBody {
    val port = ioNodeSink.bundle.port
    io <> port
  } }
}

class AXI4AsMem(masterParam: MasterPortParams)(implicit p: Parameters) extends LazyModule
{
  val addr = AddressSet.misaligned(masterParam.base, masterParam.size)
  val ranges = AddressRange.fromSets(addr)
  require (ranges.size == 1, "DDR range must be contiguous")
  val depth = ranges.head.size

  val buffer  = LazyModule(new TLBuffer)
  val toaxi4  = LazyModule(new TLToAXI4(adapterName = Some("mem"), stripBits = 1))
  val indexer = LazyModule(new AXI4IdIndexer(idBits = 4))
  val deint   = LazyModule(new AXI4Deinterleaver(p(CacheBlockBytes)))
  val yank    = LazyModule(new AXI4UserYanker)

  val device = new MemoryDevice
  val slaveParam = AXI4SlavePortParameters(
    slaves = Seq(AXI4SlaveParameters(
      address       = addr,
      resources     = device.reg,
      regionType    = RegionType.UNCACHED,
      executable    = true,
      supportsWrite = TransferSizes(1, 128),
      supportsRead  = TransferSizes(1, 128))),
    beatBytes = 8)
  val axi4node = AXI4SlaveNode(Seq(slaveParam))
  val node: TLInwardNode = axi4node := yank.node := deint.node := indexer.node := toaxi4.node := buffer.node

  lazy val module = new LazyModuleImp(this) {
    val (axi_async, _) = axi4node.in(0)
    val io = IO(new Bundle {
      val port = new AXI4Bundle( AXI4BundleParameters (
        log2Ceil(slaveParam.maxAddress+1),  // addrBits
        slaveParam.beatBytes * 8,           // dataBits
        log2Ceil(masterParam.idBits)        // idBits
      ) )
    })
    io.port <> axi_async
  }
}