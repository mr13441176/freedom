package uec.keystone.nedochip

import chisel3._
import chisel3.util._
import chisel3.experimental._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import sifive.blocks.devices.pinctrl._
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.fpgashells.clocks._

// ******* For Xilinx FPGAs

import sifive.fpgashells.devices.xilinx.xilinxvc707mig._

class TLULtoMIG(cacheBlockBytes: Int, TLparams: TLBundleParameters)(implicit p :Parameters) extends LazyModule {
  // Create the DDR
  val ddr = LazyModule(
    new XilinxVC707MIG(
      XilinxVC707MIGParams(
        address = AddressSet.misaligned(
          p(ExtMem).get.master.base,
          0x40000000L * 1 // 1GiB for the VC707DDR
        )
      )
    )
  )

  // Create a dummy node where we can attach our silly TL port
  val device = new MemoryDevice
  val node = TLClientNode(Seq.tabulate(1) { channel =>
    TLClientPortParameters(
      clients = Seq(TLClientParameters(
        name = "dummy",
        sourceId = IdRange(0, 2) // TODO: What is this?
      ))
    )
  })

  // Attach to the DDR
  ddr.buffer.node := node

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val tlport = Flipped(new TLUL(TLparams))
      var ddrport = new XilinxVC707MIGIO(ddr.depth)
    })

    val depth = ddr.depth

    //val mem_tl = Wire(HeterogeneousBag.fromNode(node.in))
    node.in.foreach {
      case  (bundle, _) =>
        bundle.a <> io.tlport.a
        io.tlport.d <> bundle.d
        //bundle.b.bits := (new TLBundleB(TLparams)).fromBits(0.U)
        bundle.b.ready := false.B
        bundle.c.valid := false.B
        bundle.c.bits := 0.U.asTypeOf(new TLBundleC(TLparams))
        bundle.e.valid := false.B
        bundle.e.bits := 0.U.asTypeOf(new TLBundleE(TLparams))
    }

    // Create the actual module, and attach the DDR port
    io.ddrport <> ddr.module.io.port
  }

}

// ** For Quartus-based FPGAs

class QuartusDDR extends Bundle {
  val mem_a                = Output(Bits((14).W))
  val mem_ba               = Output(Bits((3).W))
  val mem_ck               = Output(Bits((2).W))
  val mem_ck_n             = Output(Bits((2).W))
  val mem_cke              = Output(Bits((1).W))
  val mem_cs_n             = Output(Bits((1).W))
  val mem_dm               = Output(Bits((8).W))
  val mem_ras_n            = Output(Bool())
  val mem_cas_n            = Output(Bool())
  val mem_we_n             = Output(Bool())
  val mem_dq               = Analog(64.W)
  val mem_dqs              = Analog(8.W)
  val mem_dqs_n            = Analog(8.W)
  val mem_odt              = Output(Bits((1).W))

  //val reset_n          = Output(Bool())
}

trait QuartusClocksReset extends Bundle {
  //inputs
  //"NO_BUFFER" clock source (must be connected to IBUF outside of IP)
  val clk_clk               = Input(Bool())
  val reset_reset_n         = Input(Bool())
  val dimmclk_clk           = Output(Clock())
}

trait QuartusUserSignals extends Bundle {
  val oct_rdn               = Input(Bool())
  val oct_rup               = Input(Bool())
  val mem_status_local_init_done = Output(Bool())
  val mem_status_local_cal_success = Output(Bool())
  val mem_status_local_cal_fail = Output(Bool())
}

class QuartusIO extends QuartusDDR with QuartusUserSignals

class QuartusPlatformBlackBox(implicit val p:Parameters) extends BlackBox {
  override def desiredName = "main"

  val io = IO(new QuartusIO with QuartusClocksReset {
    //axi_s
    //slave interface write address ports
    val axi4_awid = Input(Bits((4).W))
    val axi4_awaddr = Input(Bits((32).W))
    val axi4_awlen = Input(Bits((8).W))
    val axi4_awsize = Input(Bits((3).W))
    val axi4_awburst = Input(Bits((2).W))
    val axi4_awlock = Input(Bits((1).W))
    val axi4_awcache = Input(Bits((4).W))
    val axi4_awprot = Input(Bits((3).W))
    val axi4_awqos = Input(Bits((4).W))
    val axi4_awvalid = Input(Bool())
    val axi4_awready = Output(Bool())
    //slave interface write data ports
    val axi4_wdata = Input(Bits((64).W))
    val axi4_wstrb = Input(Bits((8).W))
    val axi4_wlast = Input(Bool())
    val axi4_wvalid = Input(Bool())
    val axi4_wready = Output(Bool())
    //slave interface write response ports
    val axi4_bready = Input(Bool())
    val axi4_bid = Output(Bits((4).W))
    val axi4_bresp = Output(Bits((2).W))
    val axi4_bvalid = Output(Bool())
    //slave interface read address ports
    val axi4_arid = Input(Bits((4).W))
    val axi4_araddr = Input(Bits((32).W))
    val axi4_arlen = Input(Bits((8).W))
    val axi4_arsize = Input(Bits((3).W))
    val axi4_arburst = Input(Bits((2).W))
    val axi4_arlock = Input(Bits((1).W))
    val axi4_arcache = Input(Bits((4).W))
    val axi4_arprot = Input(Bits((3).W))
    val axi4_arqos = Input(Bits((4).W))
    val axi4_arvalid = Input(Bool())
    val axi4_arready = Output(Bool())
    //slave interface read data ports
    val axi4_rready = Input(Bool())
    val axi4_rid = Output(Bits((4).W))
    val axi4_rdata = Output(Bits((64).W))
    val axi4_rresp = Output(Bits((2).W))
    val axi4_rlast = Output(Bool())
    val axi4_rvalid = Output(Bool())
  })
}

class QuartusIsland(c : Seq[AddressSet], val crossing: ClockCrossingType = AsynchronousCrossing(8))(implicit p: Parameters) extends LazyModule with CrossesToOnlyOneClockDomain {
  val ranges = AddressRange.fromSets(c)
  require (ranges.size == 1, "DDR range must be contiguous")
  val offset = ranges.head.base
  val depth = ranges.head.size
  require((depth<=0x100000000L),"vc707mig supports upto 4GB depth configuraton")

  val device = new MemoryDevice
  val node = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    slaves = Seq(AXI4SlaveParameters(
      address       = c,
      resources     = device.reg,
      regionType    = RegionType.UNCACHED,
      executable    = true,
      supportsWrite = TransferSizes(1, 128),
      supportsRead  = TransferSizes(1, 128))),
    beatBytes = 8)))

  lazy val module = new LazyRawModuleImp(this) {
    val io = IO(new Bundle {
      val port = new QuartusIO
      val ckrst = new Bundle with QuartusClocksReset
    })

    childClock := io.ckrst.dimmclk_clk
    childReset := io.ckrst.reset_reset_n

    //MIG black box instantiation
    val blackbox = Module(new QuartusPlatformBlackBox)
    val (axi_async, _) = node.in(0)

    //pins to top level

    //inouts
    attach(io.port.mem_dq,blackbox.io.mem_dq)
    attach(io.port.mem_dqs_n,blackbox.io.mem_dqs_n)
    attach(io.port.mem_dqs,blackbox.io.mem_dqs)

    //outputs
    io.port.mem_a            := blackbox.io.mem_a
    io.port.mem_ba           := blackbox.io.mem_ba
    io.port.mem_ras_n        := blackbox.io.mem_ras_n
    io.port.mem_cas_n        := blackbox.io.mem_cas_n
    io.port.mem_we_n         := blackbox.io.mem_we_n
    io.port.mem_ck           := blackbox.io.mem_ck
    io.port.mem_ck_n         := blackbox.io.mem_ck_n
    io.port.mem_cke          := blackbox.io.mem_cke
    io.port.mem_cs_n         := blackbox.io.mem_cs_n
    io.port.mem_dm           := blackbox.io.mem_dm
    io.port.mem_odt          := blackbox.io.mem_odt

    //inputs
    //NO_BUFFER clock
    blackbox.io.clk_clk       := io.ckrst.clk_clk
    blackbox.io.reset_reset_n := io.ckrst.reset_reset_n
    io.ckrst.dimmclk_clk       := blackbox.io.dimmclk_clk
    blackbox.io.oct_rdn       := io.port.oct_rdn
    blackbox.io.oct_rup       := io.port.oct_rup
    io.port.mem_status_local_init_done := blackbox.io.mem_status_local_init_done
    io.port.mem_status_local_cal_success := blackbox.io.mem_status_local_cal_success
    io.port.mem_status_local_cal_fail := blackbox.io.mem_status_local_cal_fail

    val awaddr = axi_async.aw.bits.addr - UInt(offset)
    val araddr = axi_async.ar.bits.addr - UInt(offset)

    //slave AXI interface write address ports
    blackbox.io.axi4_awid    := axi_async.aw.bits.id
    blackbox.io.axi4_awaddr  := awaddr //truncated
    blackbox.io.axi4_awlen   := axi_async.aw.bits.len
    blackbox.io.axi4_awsize  := axi_async.aw.bits.size
    blackbox.io.axi4_awburst := axi_async.aw.bits.burst
    blackbox.io.axi4_awlock  := axi_async.aw.bits.lock
    blackbox.io.axi4_awcache := UInt("b0011")
    blackbox.io.axi4_awprot  := axi_async.aw.bits.prot
    blackbox.io.axi4_awqos   := axi_async.aw.bits.qos
    blackbox.io.axi4_awvalid := axi_async.aw.valid
    axi_async.aw.ready        := blackbox.io.axi4_awready

    //slave interface write data ports
    blackbox.io.axi4_wdata   := axi_async.w.bits.data
    blackbox.io.axi4_wstrb   := axi_async.w.bits.strb
    blackbox.io.axi4_wlast   := axi_async.w.bits.last
    blackbox.io.axi4_wvalid  := axi_async.w.valid
    axi_async.w.ready         := blackbox.io.axi4_wready

    //slave interface write response
    blackbox.io.axi4_bready  := axi_async.b.ready
    axi_async.b.bits.id       := blackbox.io.axi4_bid
    axi_async.b.bits.resp     := blackbox.io.axi4_bresp
    axi_async.b.valid         := blackbox.io.axi4_bvalid

    //slave AXI interface read address ports
    blackbox.io.axi4_arid    := axi_async.ar.bits.id
    blackbox.io.axi4_araddr  := araddr // truncated
    blackbox.io.axi4_arlen   := axi_async.ar.bits.len
    blackbox.io.axi4_arsize  := axi_async.ar.bits.size
    blackbox.io.axi4_arburst := axi_async.ar.bits.burst
    blackbox.io.axi4_arlock  := axi_async.ar.bits.lock
    blackbox.io.axi4_arcache := UInt("b0011")
    blackbox.io.axi4_arprot  := axi_async.ar.bits.prot
    blackbox.io.axi4_arqos   := axi_async.ar.bits.qos
    blackbox.io.axi4_arvalid := axi_async.ar.valid
    axi_async.ar.ready        := blackbox.io.axi4_arready

    //slace AXI interface read data ports
    blackbox.io.axi4_rready  := axi_async.r.ready
    axi_async.r.bits.id       := blackbox.io.axi4_rid
    axi_async.r.bits.data     := blackbox.io.axi4_rdata
    axi_async.r.bits.resp     := blackbox.io.axi4_rresp
    axi_async.r.bits.last     := blackbox.io.axi4_rlast
    axi_async.r.valid         := blackbox.io.axi4_rvalid
  }
}

class QuartusPlatform(c : Seq[AddressSet], crossing: ClockCrossingType = AsynchronousCrossing(8))(implicit p: Parameters) extends LazyModule {
  val ranges = AddressRange.fromSets(c)
  val depth = ranges.head.size

  val buffer  = LazyModule(new TLBuffer)
  val toaxi4  = LazyModule(new TLToAXI4(adapterName = Some("mem"), stripBits = 1))
  val indexer = LazyModule(new AXI4IdIndexer(idBits = 4))
  val deint   = LazyModule(new AXI4Deinterleaver(p(CacheBlockBytes)))
  val yank    = LazyModule(new AXI4UserYanker)
  val island  = LazyModule(new QuartusIsland(c, crossing))

  val node: TLInwardNode =
    island.crossAXI4In(island.node) := yank.node := deint.node := indexer.node := toaxi4.node := buffer.node

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val port = new QuartusIO
      val ckrst = new Bundle with QuartusClocksReset
    })

    io.port <> island.module.io.port
    io.ckrst <> island.module.io.ckrst
  }
}


class TLULtoQuartusPlatform(cacheBlockBytes: Int, TLparams: TLBundleParameters)(implicit p :Parameters)
  extends LazyModule {
  // Create the DDR
  val ddr = LazyModule(
    new QuartusPlatform(
      AddressSet.misaligned(
          p(ExtMem).get.master.base,
          0x40000000L * 1 // 1GiB for the VC707DDR
      )
    )
  )

  // Create a dummy node where we can attach our silly TL port
  val device = new MemoryDevice
  val node = TLClientNode(Seq.tabulate(1) { channel =>
    TLClientPortParameters(
      clients = Seq(TLClientParameters(
        name = "dummy",
        sourceId = IdRange(0, 2) // TODO: What is this?
      ))
    )
  })

  // Attach to the DDR
  ddr.buffer.node := node

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val tlport = Flipped(new TLUL(TLparams))
      var qport = new QuartusIO
      val ckrst = new Bundle with QuartusClocksReset
    })

    val depth = ddr.depth

    //val mem_tl = Wire(HeterogeneousBag.fromNode(node.in))
    node.in.foreach {
      case  (bundle, _) =>
        bundle.a <> io.tlport.a
        io.tlport.d <> bundle.d
        //bundle.b.bits := (new TLBundleB(TLparams)).fromBits(0.U)
        bundle.b.ready := false.B
        bundle.c.valid := false.B
        bundle.c.bits := 0.U.asTypeOf(new TLBundleC(TLparams))
        bundle.e.valid := false.B
        bundle.e.bits := 0.U.asTypeOf(new TLBundleE(TLparams))
    }

    // Create the actual module, and attach the port
    io.qport <> ddr.module.io.port
    io.ckrst <> ddr.module.io.ckrst
  }

}