package uec.keystone.nedochip

import chisel3._
import chisel3.util._
import chisel3.experimental._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import sifive.blocks.devices.pinctrl._
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.fpgashells.clocks._

// *********************************************************************************
// NEDO wrapper - for doing a wrapper with actual ports (tri-state buffers at least)
// *********************************************************************************

class NEDOwrapper(implicit p :Parameters) extends RawModule {
  // The actual pins of this module.
  // This is a list of the ports 'to be wirebonded' / 'from the package'
  val clk_p = IO(Analog(1.W))
  val clk_n = IO(Analog(1.W))
  val rst_n = IO(Analog(1.W))
  val gpio = IO(Vec(p(PeripheryGPIOKey).head.width, Analog(1.W)))
  val jtag_tdi = IO(Analog(1.W))
  val jtag_tdo = IO(Analog(1.W))
  val jtag_tck = IO(Analog(1.W))
  val jtag_tms = IO(Analog(1.W))
  val jtag_rst = IO(Analog(1.W))
  val spi_cs = IO(Vec(p(PeripherySPIKey).head.csWidth, Analog(1.W)))
  val spi_sck = IO(Analog(1.W))
  val spi_dq = IO(Vec(4, Analog(1.W)))
  val qspi_cs =  IO(Vec(p(PeripherySPIFlashKey).head.csWidth, Analog(1.W)))
  val qspi_sck = IO(Analog(1.W))
  val qspi_dq = IO(Vec(4, Analog(1.W)))
  //val i2c_sda = IO(Analog(1.W))
  //val i2c_scl = IO(Analog(1.W))
  val uart_txd = IO(Analog(1.W))
  val uart_rxd = IO(Analog(1.W))

  // This clock and reset are only declared. We soon connect them
  val clock = Wire(Clock())
  val reset = Wire(Bool())

  // An option to dynamically assign
  var tlport : Option[TLUL] = None

  // All the modules declared here have this clock and reset
  withClockAndReset(clock, reset) {
    // Main clock
    val clock_XTAL = Module(new XTAL_DRV)
    clock := clock_XTAL.io.C
    clock_XTAL.io.E := true.B // Always enabled
    attach(clk_p, clock_XTAL.io.XP)
    attach(clk_n, clock_XTAL.io.XN)

    // Main reset
    val rst_gpio = Module(new GPIO_24_A)
    reset := !PinToGPIO_24_A.asInput(rst_gpio.io)
    attach(rst_n, rst_gpio.io.PAD)

    // The platform module
    val system = Module(new NEDOPlatform)

    // GPIOs
    (gpio zip system.io.pins.gpio.pins).foreach {
      case (g, i) =>
        val GPIO = Module(new GPIO_24_A)
        attach(g, GPIO.io.PAD)
        PinToGPIO_24_A(GPIO.io, i)
    }

    // JTAG
    val JTAG_TMS = Module(new GPIO_24_A)
    PinToGPIO_24_A(JTAG_TMS.io, system.io.pins.jtag.TMS)
    attach(jtag_tck, JTAG_TMS.io.PAD)
    val JTAG_TCK = Module(new GPIO_24_A)
    PinToGPIO_24_A(JTAG_TCK.io, system.io.pins.jtag.TCK)
    attach(jtag_tck, JTAG_TCK.io.PAD)
    val JTAG_TDI = Module(new GPIO_24_A)
    PinToGPIO_24_A(JTAG_TDI.io, system.io.pins.jtag.TDI)
    attach(jtag_tck, JTAG_TDI.io.PAD)
    val JTAG_TDO = Module(new GPIO_24_A)
    PinToGPIO_24_A(JTAG_TDO.io, system.io.pins.jtag.TDO)
    attach(jtag_tck, JTAG_TDO.io.PAD)
    val JTAG_RST = Module(new GPIO_24_A)
    system.io.jtag_reset := PinToGPIO_24_A.asInput(JTAG_RST.io)
    attach(jtag_rst, JTAG_RST.io.PAD)

    // QSPI (SPI as flash memory)
    (qspi_cs zip system.io.pins.qspi.cs).foreach {
      case (g, i) =>
        val QSPI_CS = Module(new GPIO_24_A)
        PinToGPIO_24_A(QSPI_CS.io, i)
        attach(g, QSPI_CS.io.PAD)
    }
    (qspi_dq zip system.io.pins.qspi.dq).foreach {
      case (g, i) =>
        val QSPI_DQ = Module(new GPIO_24_A)
        PinToGPIO_24_A(QSPI_DQ.io, i)
        attach(g, QSPI_DQ.io.PAD)
    }
    val QSPI_SCK = Module(new GPIO_24_A)
    PinToGPIO_24_A(QSPI_SCK.io, system.io.pins.qspi.sck)
    attach(qspi_sck, QSPI_SCK.io.PAD)

    // SPI (SPI as SD?)
    (spi_cs zip system.io.pins.spi.cs).foreach {
      case (g, i) =>
        val SPI_CS = Module(new GPIO_24_A)
        PinToGPIO_24_A(SPI_CS.io, i)
        attach(g, SPI_CS.io.PAD)
    }
    (spi_dq zip system.io.pins.spi.dq).foreach {
      case (g, i) =>
        val SPI_DQ = Module(new GPIO_24_A)
        PinToGPIO_24_A(SPI_DQ.io, i)
        attach(g, SPI_DQ.io.PAD)
    }
    val SPI_SCK = Module(new GPIO_24_A)
    PinToGPIO_24_A(SPI_SCK.io, system.io.pins.spi.sck)
    attach(spi_sck, SPI_SCK.io.PAD)

    // UART
    val UART_RXD = Module(new GPIO_24_A)
    PinToGPIO_24_A(UART_RXD.io, system.io.pins.uart.rxd)
    attach(uart_rxd, UART_RXD.io.PAD)
    val UART_TXD = Module(new GPIO_24_A)
    PinToGPIO_24_A(UART_TXD.io, system.io.pins.uart.txd)
    attach(uart_txd, UART_TXD.io.PAD)

    // I2C
    //val I2C_SDA = Module(new GPIO_24_A)
    //PinToGPIO_24_A(I2C_SDA.io, system.io.pins.i2c.sda)
    //attach(i2c_sda, I2C_SDA.io.PAD)
    //val I2C_SCL = Module(new GPIO_24_A)
    //PinToGPIO_24_A(I2C_SCL.io, system.io.pins.i2c.scl)
    //attach(i2c_scl, I2C_SCL.io.PAD)

    // The memory port
    // TODO: This is awfully dirty. I mean it should get the work done
    //system.io.tlport.getElements.head
    tlport = Some(IO(new TLUL(system.sys.outer.memTLNode.head.in.head._1.params)))
    tlport.get <> system.io.tlport
  }
}

// **********************************************************************
// ** NEDO chip - for doing the only-input/output chip
// **********************************************************************

class NEDObase(implicit val p :Parameters) extends RawModule {
  // The actual pins of this module.
  val gpio_in = IO(Input(UInt(p(GPIOInKey).W)))
  val gpio_out = IO(Output(UInt((p(PeripheryGPIOKey).head.width-p(GPIOInKey)).W)))
  val jtag = IO(new Bundle {
    val jtag_TDI = (Input(Bool()))
    val jtag_TDO = (Output(Bool()))
    val jtag_TCK = (Input(Bool()))
    val jtag_TMS = (Input(Bool()))
  })
  val sdio = IO(new Bundle {
    val sdio_clk = (Output(Bool()))
    val sdio_cmd = (Output(Bool()))
    val sdio_dat_0 = (Input(Bool()))
    val sdio_dat_1 = (Analog(1.W))
    val sdio_dat_2 = (Analog(1.W))
    val sdio_dat_3 = (Output(Bool()))
  })
  val qspi = IO(new Bundle {
    val qspi_cs = (Output(UInt(p(PeripherySPIFlashKey).head.csWidth.W)))
    val qspi_sck = (Output(Bool()))
    val qspi_miso = (Input(Bool()))
    val qspi_mosi = (Output(Bool()))
    val qspi_wp = (Output(Bool()))
    val qspi_hold = (Output(Bool()))
  })
  val uart_txd = IO(Output(Bool()))
  val uart_rxd = IO(Input(Bool()))
  val uart_rtsn = IO(Output(Bool()))
  val uart_ctsn = IO(Input(Bool()))
  // These are later connected
  val clock = Wire(Clock())
  val reset = Wire(Bool()) // System reset (for cores)
  val areset = Wire(Bool()) // Global reset (a BUFFd version of the reset from the button)
  val ndreset = Wire(Bool()) // Debug reset (The reset you can trigger from JTAG)
  // An option to dynamically assign
  var tlportw : Option[TLUL] = None
  var cacheBlockBytesOpt: Option[Int] = None

  // All the modules declared here have this clock and reset
  withClockAndReset(clock, reset) {
    // The platform module
    val system = Module(new NEDOPlatform)
    ndreset := system.io.ndreset
    cacheBlockBytesOpt = Some(system.sys.outer.cacheBlockBytes)

    // Merge all the gpio vector
    val vgpio_in = VecInit(gpio_in.toBools)
    val vgpio_out = Wire(Vec(p(PeripheryGPIOKey).head.width-p(GPIOInKey), Bool()))
    gpio_out := vgpio_out.asUInt()
    val gpio = vgpio_in ++ vgpio_out
    // GPIOs
    (gpio zip system.io.pins.gpio.pins).zipWithIndex.foreach {
      case ((g: Bool, pin: BasePin), i: Int) =>
        if(i < p(GPIOInKey)) BasePinToRegular(pin, g)
        else g := BasePinToRegular(pin)
    }

    // JTAG
    BasePinToRegular(system.io.pins.jtag.TMS, jtag.jtag_TMS)
    BasePinToRegular(system.io.pins.jtag.TCK, jtag.jtag_TCK)
    BasePinToRegular(system.io.pins.jtag.TDI, jtag.jtag_TDI)
    jtag.jtag_TDO := BasePinToRegular(system.io.pins.jtag.TDO)
    system.io.jtag_reset := areset

    // QSPI (SPI as flash memory)
    qspi.qspi_cs := BasePinToRegular(system.io.pins.qspi.cs)
    qspi.qspi_sck := BasePinToRegular(system.io.pins.qspi.sck)
    BasePinToRegular(system.io.pins.qspi.dq(0), qspi.qspi_miso)
    qspi.qspi_mosi := BasePinToRegular(system.io.pins.qspi.dq(1))
    qspi.qspi_wp := BasePinToRegular(system.io.pins.qspi.dq(2))
    qspi.qspi_hold := BasePinToRegular(system.io.pins.qspi.dq(3))

    // SPI (SPI as SD?)
    sdio.sdio_dat_3 := BasePinToRegular(system.io.pins.spi.cs.head)
    sdio.sdio_clk := BasePinToRegular(system.io.pins.spi.sck)
    BasePinToRegular(system.io.pins.spi.dq(0), RegNext(RegNext(sdio.sdio_dat_0))) // NOTE: We saw like this on SDIOOverlay
    sdio.sdio_cmd := BasePinToRegular(system.io.pins.spi.dq(1))
    BasePinToRegular(system.io.pins.spi.dq(2)) // Ignored
    BasePinToRegular(system.io.pins.spi.dq(3)) // Ignored

    // UART
    BasePinToRegular(system.io.pins.uart.rxd, uart_rxd)
    uart_txd := BasePinToRegular(system.io.pins.uart.txd)
    uart_rtsn := false.B

    // The memory port
    tlportw = Some(system.io.tlport)
  }
  val cacheBlockBytes = cacheBlockBytesOpt.get
}

class NEDOchip(implicit override val p :Parameters) extends NEDObase {
  // Some additional ports to connect to the chip
  val sys_clk = IO(Input(Clock()))
  val rst_n = IO(Input(Bool()))
  val jrst_n = IO(Input(Bool()))
  val tlport = IO(new TLUL(tlportw.get.params))
  // TL port connection
  tlport.a <> tlportw.get.a
  tlportw.get.d <> tlport.d
  // Clock and reset connection
  clock := sys_clk
  reset := !rst_n | ndreset
  areset := !jrst_n
}

// ********************************************************************
// NEDODPGA - Just an adaptation of NEDOchip to the FPGA
// ********************************************************************
import sifive.fpgashells.ip.xilinx.vc707mig._
import sifive.fpgashells.ip.xilinx._

class NEDOFPGA(implicit override val p :Parameters) extends NEDObase {
  var ddr: Option[VC707MIGIODDR] = None
  val sys_clock_p = IO(Input(Clock()))
  val sys_clock_n = IO(Input(Clock()))
  val sys_clock_ibufds = Module(new IBUFDS())
  val sys_clk_i = sys_clock_ibufds.io.O
  sys_clock_ibufds.io.I := sys_clock_p
  sys_clock_ibufds.io.IB := sys_clock_n
  val rst_0 = IO(Input(Bool()))
  val rst_1 = IO(Input(Bool()))
  val rst_2 = IO(Input(Bool()))
  val reset_0 = IBUF(rst_0)
  val reset_1 = IBUF(rst_1)
  val reset_2 = IBUF(rst_2)

  withClockAndReset(clock, reset) {
    // Instance our converter, and connect everything
    val mod = Module(LazyModule(new TLULtoMIG(cacheBlockBytes, tlportw.get.params)).module)

    // DDR port only
    ddr = Some(IO(new VC707MIGIODDR(mod.depth)))
    ddr.get <> mod.io.ddrport

    // TileLink Interface from platform
    mod.io.tlport.a <> tlportw.get.a
    tlportw.get.d <> mod.io.tlport.d

    // PLL instance
    val c = new PLLParameters(
      name ="pll",
      input = PLLInClockParameters(
        freqMHz = 200.0,
        feedback = true
      ),
      req = Seq(
        PLLOutClockParameters(
          freqMHz = 50.0
        )
      )
    )
    val pll = Module(new Series7MMCM(c))
    pll.io.clk_in1 := sys_clk_i
    pll.io.reset := reset_0

    // MIG connections, like resets and stuff
    mod.io.ddrport.sys_clk_i := sys_clk_i.asUInt()
    mod.io.ddrport.aresetn := !ResetCatchAndSync(pll.io.clk_out1.get, reset_0, 20) // TODO: Is delayed, but I am not sure
    mod.io.ddrport.sys_rst := areset // This is ok

    // Main clock and reset assignments
    clock := pll.io.clk_out1.get // Hopefully this is ok
    reset := reset_1 | ndreset
    areset := reset_2 | ndreset
  }
}

// ********************************************************************
// NEDODPGAQuartus - Just an adaptation of NEDOchip to the FPGA in Quartus
// ********************************************************************

class NEDOFPGAQuartus(implicit override val p :Parameters) extends NEDObase {
  val ddr = IO( new QuartusIO )
  val clk = IO(Input(Clock()))
  val reset_0_n = IO(Input(Bool()))
  val reset_1_n = IO(Input(Bool()))
  val reset_2_n = IO(Input(Bool()))

  withClockAndReset(clock, reset) {
    // Instance our converter, and connect everything
    val mod = Module(LazyModule(new TLULtoQuartusPlatform(cacheBlockBytes, tlportw.get.params)).module)

    // DDR port only\
    ddr <> mod.io.qport

    // TileLink Interface from platform
    mod.io.tlport.a <> tlportw.get.a
    tlportw.get.d <> mod.io.tlport.d

    // MIG connections, like resets and stuff
    mod.io.ckrst.clk_clk := clk.asUInt()
    mod.io.ckrst.reset_reset_n := reset_0_n

    // Main clock and reset assignments
    clock := mod.io.ckrst.dimmclk_clk
    reset := !reset_1_n | ndreset
    areset := !reset_2_n
  }
}