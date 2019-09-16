package uec.keystone.nedochip

import chisel3._
import chisel3.util._
import chisel3.experimental._
import freechips.rocketchip.config._
import sifive.blocks.devices.pinctrl.{PinCtrl, Pin, BasePin, EnhancedPin, EnhancedPinCtrl}
import sifive.blocks.devices.gpio._

class GPIO_24_A extends BlackBox {
  val io = IO(new Bundle{
    val IE = Input(Bool())
    val OE = Input(Bool())
    val DS = Input(Bool())
    val PE = Input(Bool())
    val I = Input(Bool())
    val O = Output(Bool())
    val PAD = Analog(1.W)
  })
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
  val clk = IO(Analog(1.W))
  val rst_n = IO(Analog(1.W))
  val gkeys = p(PeripheryGPIOKey).head
  val gpio = IO(Vec(gkeys.width, Analog(1.W)))
  // ..

  val clock_XTAL = Module(new XTAL_DRV)
  val clock = clock_XTAL.io.C
  clock_XTAL.io.E := true.B

  val rst_gpio = Module(new GPIO_24_A)
  val reset = !rst_gpio.io.O
  // Do the rest of the connections

  withClockAndReset(clock, reset) {
    val system = Module(new NEDOPlatform)
    system.io.pins.gpio

    // Connect everything
    (gpio zip system.io.pins.gpio.pins).foreach{
      case (g,i) =>
        val gm = Module(new GPIO_24_A)
        attach(g, gm.io.PAD)
        gm.io.I := i.i.ival
        i.o.oval := gm.io.O
        // Continue to put the ports according to
    }
  }
}