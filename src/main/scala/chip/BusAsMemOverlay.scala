package uec.freedom.u500

import chisel3._
import chisel3.util._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import sifive.fpgashells.shell._

case class BusAsMemOverlayParams(masterParam: MasterPortParams, cacheBlockBytes: Int)(implicit val p: Parameters)
case object BusAsMemOverlayKey extends Field[Seq[DesignOverlay[BusAsMemOverlayParams, TLInwardNode]]](Nil)

abstract class AXI4AsMemOverlay[IO <: Data](val params: BusAsMemOverlayParams)
  extends IOOverlay[AXI4Bundle, TLInwardNode]
{
  implicit val p = params.p

  val AXI4OutAsMem = LazyModule(new AXI4AsMem(params.masterParam, params.cacheBlockBytes))
  val ioNode = BundleBridgeSource(() => AXI4OutAsMem.module.io.cloneType)
  val ioNodeSink = shell { ioNode.makeSink() }

  def ioFactory = new AXI4Bundle(AXI4OutAsMem.module.axi_async.params)
  def designOutput = AXI4OutAsMem.node

  InModuleBody {
    ioNode.bundle <> AXI4OutAsMem.module.io
  }

  shell { InModuleBody {
    val port = ioNodeSink.bundle.port
    io <> port
  } }
}

class AXI4AsMem(masterParam: MasterPortParams, cacheBlockBytes: Int)(implicit p: Parameters) extends LazyModule
{
  val addr = AddressSet.misaligned(masterParam.base, masterParam.size)
  val ranges = AddressRange.fromSets(addr)
  require (ranges.size == 1, "DDR range must be contiguous")

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
      supportsWrite = TransferSizes(1, cacheBlockBytes),
      supportsRead  = TransferSizes(1, cacheBlockBytes)
    )),
    beatBytes = 8)
  val axi4node = AXI4SlaveNode(Seq(slaveParam))
  val node: TLInwardNode = axi4node := yank.node := deint.node := indexer.node := toaxi4.node := buffer.node

  lazy val module = new LazyModuleImp(this) {
    val (axi_async, _) = axi4node.in(0)
    val io = IO(new Bundle {
      val port = new AXI4Bundle(axi_async.params)
    })
    io.port <> axi_async
  }
}

class TLPinOut(val params: TLBundleParameters) extends Bundle {
  // use channel a & d only. Drop b c e channels
  val a = Decoupled(new TLBundleA(params))
  val d = Flipped(Decoupled(new TLBundleD(params)))
}

abstract class TLAsMemOverlay[IO <: Data](val params: BusAsMemOverlayParams)
  extends IOOverlay[TLPinOut, TLInwardNode]
{
  implicit val p = params.p

  val TLOutAsMem = LazyModule(new TLAsMem(params.masterParam, params.cacheBlockBytes))
  val ioNode = BundleBridgeSource(() => TLOutAsMem.module.io.cloneType)
  val ioNodeSink = shell { ioNode.makeSink() }

  def ioFactory = new TLPinOut(TLOutAsMem.module.tl_async.params)
  def designOutput = TLOutAsMem.node

  InModuleBody {
    ioNode.bundle.a <> TLOutAsMem.module.io.a
    TLOutAsMem.module.io.d <> ioNode.bundle.d
  }

  shell { InModuleBody {
    io.a <> ioNodeSink.bundle.a
    ioNodeSink.bundle.d <> io.d
  } }
}

class TLAsMem(masterParam: MasterPortParams, cacheBlockBytes: Int)(implicit p: Parameters) extends LazyModule
{
  val addr = AddressSet.misaligned(masterParam.base, masterParam.size)
  val ranges = AddressRange.fromSets(addr)
  require (ranges.size == 1, "DDR range must be contiguous")

  val buffer  = LazyModule(new TLBuffer)

  val device = new MemoryDevice
  val managerParam = TLManagerPortParameters(
    managers = Seq(TLManagerParameters(
      address = addr,
      resources = device.reg,
      regionType = RegionType.UNCACHED, // cacheable
      executable = true,
      supportsGet = TransferSizes(1, cacheBlockBytes),
      supportsPutFull = TransferSizes(1, cacheBlockBytes),
      supportsPutPartial = TransferSizes(1, cacheBlockBytes)
    )),
    beatBytes = masterParam.beatBytes
  )
  val tlnode = TLManagerNode(Seq(managerParam))
  val node: TLInwardNode = tlnode := buffer.node

  lazy val module = new LazyModuleImp(this) {
    val (tl_async, _) = tlnode.in(0)
    val io = IO(new TLPinOut(tl_async.params))
    io.a <> tl_async.a
    //tl_async.d <> io.d

    //dont't know why the TLToAXI4 removes d.bits.param & d.bits.sink signals
    //because this has to be connected with a TLToAXI4 outside,
    //  so in here we have to connect the other signals (besides of the two) manually
    tl_async.d.ready <> io.d.ready
    tl_async.d.valid <> io.d.valid
    tl_async.d.bits.opcode <> io.d.bits.opcode
    tl_async.d.bits.size <> io.d.bits.size
    tl_async.d.bits.source <> io.d.bits.source
    tl_async.d.bits.denied <> io.d.bits.denied
    tl_async.d.bits.data <> io.d.bits.data
    tl_async.d.bits.corrupt <> io.d.bits.corrupt
    //then on the top level of verilog code, remove the two unused signals of
    //  tlout_d_bits_param & tlout_d_bits_sink
  }
}
