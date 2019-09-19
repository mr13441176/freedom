package uec.keystone.nedochip

import chisel3._
import chisel3.util._
import chisel3.experimental._
import freechips.rocketchip.config._
import sifive.blocks.devices.pinctrl.{BasePin, EnhancedPin, EnhancedPinCtrl, Pin, PinCtrl}
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._

// The port of the GPIOs
class GPIO_24_A_PORT extends Bundle {
  val IE = Input(Bool())
  val OE = Input(Bool())
  val DS = Input(Bool())
  val PE = Input(Bool())
  val O = Input(Bool())
  val I = Output(Bool())
  val PAD = Analog(1.W)
}

// The blackbox instantiation for the GPIOs
class GPIO_24_A extends BlackBox {
  val io = IO(new GPIO_24_A_PORT)
}

// BasePin or ExtendedPin to the GPIO port conversion
object PinToGPIO_24_A {
  def apply(io: GPIO_24_A_PORT, pin: EnhancedPin): Unit = {
    io.DS := pin.o.ds
    io.PE := pin.o.pue
    io.O := pin.o.oval
    io.OE := pin.o.oe
    io.IE := pin.o.ie
    pin.i.ival := io.I
  }
  def apply(io: GPIO_24_A_PORT, pin: BasePin, pullup: Boolean = false): Unit = {
    io.DS := false.B
    io.PE := pullup.B
    io.O := pin.o.oval
    io.OE := pin.o.oe
    io.IE := pin.o.ie
    pin.i.ival := io.I
  }
  def asOutput(io: GPIO_24_A_PORT, in: Bool): Unit = {
    io.DS := false.B
    io.PE := false.B
    io.O := in
    io.OE := true.B
    io.IE := false.B
    //io.I ignored
  }
  def asInput(io: GPIO_24_A_PORT, pullup: Boolean = false): Bool = {
    io.DS := false.B
    io.PE := pullup.B
    io.O := false.B
    io.OE := false.B
    io.IE := true.B
    io.I
  }
}

class XTAL_DRV extends BlackBox {
  val io = IO(new Bundle{
    val E = Input(Bool())
    val C = Output(Clock())
    val XP = Analog(1.W)
    val XN = Analog(1.W)
  })
}

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
  val i2c_sda = IO(Analog(1.W))
  val i2c_scl = IO(Analog(1.W))
  val uart_txd = IO(Analog(1.W))
  val uart_rxd = IO(Analog(1.W))

  // This clock and reset are only declared. We soon connect them
  val clock = Wire(Clock())
  val reset = Wire(Bool())

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
    val I2C_SDA = Module(new GPIO_24_A)
    PinToGPIO_24_A(I2C_SDA.io, system.io.pins.i2c.sda)
    attach(i2c_sda, I2C_SDA.io.PAD)
    val I2C_SCL = Module(new GPIO_24_A)
    PinToGPIO_24_A(I2C_SCL.io, system.io.pins.i2c.scl)
    attach(i2c_scl, I2C_SCL.io.PAD)

    // The memory port
    // TODO: This is awfully dirty. I mean it should get the work done
    system.io.tlport.getElements.head
  }
}