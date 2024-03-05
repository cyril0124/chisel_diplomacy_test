package DiplomacyTest.RequestFields

import circt.stage.{ChiselStage, FirtoolOption}
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._
import org.chipsalliance.cde.config._

// simple bundle field (only one signal)
case object TestFieldKey extends ControlKey[Bool]("test") // "test" is the final echo field name
case class TestField() extends SimpleBundleField(TestFieldKey)(Output(Bool()), false.B)

case object TestField2Key extends ControlKey[Bool]("test2")
case class TestField2() extends SimpleBundleField(TestField2Key)(Output(Bool()), false.B)


// normal bundle field (more than one signal)
class TestBundle extends Bundle {
  val boolValue1 = Bool()
  val boolValue2 = Bool()
  val uintValue1 = UInt(3.W)
  val uintValue2 = UInt(8.W)
}
case object TestField3Key extends ControlKey[TestBundle]("test3")
case class TestField3() extends BundleField(TestField3Key, Output(new TestBundle), { x: TestBundle => x := DontCare })


class TestClientModule()(implicit p: Parameters) extends LazyModule {

  val node = TLClientNode(Seq(
      TLMasterPortParameters.v1(
        clients =
          Seq(TLMasterParameters.v1(
              name = "test client module",
              sourceId = IdRange(0, 10),
              supportsProbe = TransferSizes(32), // this param is used for enabling TL-C
          )),
        minLatency = 0,

        echoFields = Nil,

        // Keys and Fields:
        // 1. Keys are used for <matching> a specific user field data
        // 2. Fields are used for <defining> ...
        // 3. RequestFields ==> channel A/C/E
        // 4. RespondFields ==> channel D

        // 1. requestFields are constructed via Seq(), if you have more than one field to be used, operator "+:" may be useful
        // 2. a field is compose of two part, one is [Key] object, the other is [Field] case class
        // 3. for ClientNode requestFileds is the user bits send by channel A, ManagerNode should define the correspond key obj in its responseKeys
        // requestFields = Seq(TestField()), // single field
        requestFields = Seq(TestField()), // multiple fields

        responseKeys = Seq(TestField2Key)
      )
    )
  )

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val (out, _) = node.out(0)

    out.a.valid := false.B
    out.a.bits := DontCare

    out.a.bits.user.apply(TestFieldKey) := false.B

    assert(out.d.bits.user.lift(TestField2Key).getOrElse(true.B) === false.B)

    dontTouch(out.a)
    dontTouch(out.b)
    dontTouch(out.c)
    dontTouch(out.d)
    dontTouch(out.e)
  }
}

class TestManagerModule()(implicit p: Parameters) extends LazyModule {

  val node = TLManagerNode(Seq(
      TLSlavePortParameters.v1(
        managers = //Nil,
        Seq(TLSlaveParameters.v1(
            address = Seq(AddressSet(base = 0x200, mask = 0xff)),
            regionType = RegionType.CACHED,
            supportsAcquireT = TransferSizes(32),
            supportsAcquireB = TransferSizes(32)
        )),
        beatBytes = 32,
        endSinkId = 32,
        minLatency = 0,

        responseFields = Seq(TestField2()),

        // request keys are the user field keys required by the manager
        requestKeys = Seq(TestFieldKey)
      ),      
    )
  )

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val (in, _) = node.in(0)

    in.d := DontCare

    assert(in.a.bits.user.lift(TestFieldKey).getOrElse(true.B) === false.B)

    in.d.bits.user.apply(TestField2Key) := false.B

    dontTouch(in.a)
    dontTouch(in.b)
    dontTouch(in.c)
    dontTouch(in.d)
    dontTouch(in.e)
  }
}

class TestTop()(implicit p: Parameters) extends LazyModule {
  val client = LazyModule(new TestClientModule())
  val manager = LazyModule(new TestManagerModule())

  manager.node := client.node

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {

    val io = IO(new Bundle{
      val val3 = Output(Bool())
    })

    io.val3 := false.B

    dontTouch(io.val3)
  }
}

object main extends App {

  val config = new Config(Parameters.empty)

  // [EnableMonitors] [DisableMonitors] is used for enabling channel monitor where monitors check bus action correctness
  val top = DisableMonitors(p => LazyModule(new TestTop()(p)))(config)

  (new ChiselStage).execute(Array("--target", "verilog") ++ args, Seq(
    ChiselGeneratorAnnotation(() => top.module),
    FirtoolOption("-O=release"),
    FirtoolOption("--disable-all-randomization"),
    FirtoolOption("--disable-annotation-unknown"),
    FirtoolOption("--strip-debug-info"),
    FirtoolOption("--lower-memories"),
    FirtoolOption("--lowering-options=noAlwaysComb," +
      " disallowPortDeclSharing, disallowLocalVariables," +
      " emittedLineLength=120, explicitBitcast, locationInfoStyle=plain," +
    " disallowExpressionInliningInPorts, disallowMuxInlining"),
    FirtoolOption("--disable-annotation-unknown")
  ))

}