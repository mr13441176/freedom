package uec.keystoneAcc.devices.sha3

import chisel3._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.{AsyncResetRegVec, SynchronizerShiftReg}

case class SHA3Params(
                       address: BigInt,
                       width: Int)

class SHA3PortIO extends Bundle {
}

abstract class SHA3(busWidthBytes: Int, val c: SHA3Params, divisorInit: Int = 0)
                   (implicit p: Parameters)
  extends IORegisterRouter(
    RegisterRouterParams(
      name = "sha3",
      compat = Seq("uec,sha3-0"),
      base = c.address,
      beatBytes = busWidthBytes),
    new SHA3PortIO
  )
    with HasInterruptSources {

  def nInterrupts = 1

  ResourceBinding {
    Resource(ResourceAnchors.aliases, "sha3").bind(ResourceAlias(device.label))
  }

  lazy val module = new LazyModuleImp(this) {
    val magic = WireInit(BigInt("DEADBEEF",16).U(32.W))
    val save = RegInit(0.U(32.W))

    interrupts(0) := false.B

    regmap(
      SHA3CtrlRegs.magic -> Seq(RegField.r(32, magic,
        RegFieldDesc("magic_value","Magic Value", volatile=true))),
      SHA3CtrlRegs.readwrite -> Seq(RegField(32, save,
        RegFieldDesc("save","ReadWrite Value", reset=Some(0)))),
    )
  }
}

class TLSHA3(busWidthBytes: Int, params: SHA3Params)(implicit p: Parameters)
  extends SHA3(busWidthBytes, params) with HasTLControlRegMap

case class SHA3AttachParams(
   gpio: SHA3Params,
   controlBus: TLBusWrapper,
   intNode: IntInwardNode,
   controlXType: ClockCrossingType = NoCrossing,
   intXType: ClockCrossingType = NoCrossing,
   mclock: Option[ModuleValue[Clock]] = None,
   mreset: Option[ModuleValue[Bool]] = None)
 (implicit val p: Parameters)

object SHA3 {
  val nextId = {
    var i = -1; () => {
      i += 1; i
    }
  }

  def attach(params: SHA3AttachParams): TLSHA3 = {
    implicit val p = params.p
    val name = s"sha3 ${nextId()}"
    val cbus = params.controlBus
    val sha3 = LazyModule(new TLSHA3(cbus.beatBytes, params.gpio))
    sha3.suggestName(name)

    cbus.coupleTo(s"device_named_$name") {
      sha3.controlXing(params.controlXType) := TLFragmenter(cbus.beatBytes, cbus.blockBytes) := _
    }
    params.intNode := sha3.intXing(params.intXType)
    InModuleBody {
      sha3.module.clock := params.mclock.map(_.getWrappedValue).getOrElse(cbus.module.clock)
    }
    InModuleBody {
      sha3.module.reset := params.mreset.map(_.getWrappedValue).getOrElse(cbus.module.reset)
    }

    sha3
  }
}