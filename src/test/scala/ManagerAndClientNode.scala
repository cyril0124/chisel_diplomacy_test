package DiplomacyTest.ManagerAndClientNode

import circt.stage.{ChiselStage, FirtoolOption}
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import org.chipsalliance.cde.config._

class TestClientModule(latency: Int = 0)(implicit p: Parameters) extends LazyModule {

  // 1. Create node
  //    ClientNode is a kind of SourceNode, input signal is not required
  val node = TLClientNode(Seq(
      TLMasterPortParameters.v1(
        clients =
          Seq(TLMasterParameters.v1(
              name = "test client module" + " latency:" + latency,
              sourceId = IdRange(0, 10),
          )),
        minLatency = latency,
        echoFields = Nil,
        requestFields = Nil,
        responseKeys = Nil
      )
    )
  )

  // 2. Create real hardware
  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    node.out.foreach { out =>
      println("(1) [managers]", out._2.manager.managers)

      out._2.manager.managers.zipWithIndex.foreach {
        case (manager, i) => println("(2) [others]", i, manager.name, manager.nodePath)
      }
    }
    println()

    // SourceNode has no input (bundle, edge)
    assert(node.in == Nil)

    // node.out ==> Seq[(BO, EO)] 
    //              BO: Bundle Output ==> class TLBundleA/B/C/D/E ==> real hardware bundle
    //              EO: Edge Output   ==> class TLEdgeOut         ==> edge is used to propagate and negotiate parameters between nodes 
    
    /*
      class TLEdgeOut(
        client:  TLClientPortParameters,
        manager: TLManagerPortParameters,
        params:  Parameters,
        sourceInfo: SourceInfo)
        extends TLEdge(client, manager, params, sourceInfo)
      {
        ...
      }
    */
    val (out, _) = node.out(0)


    out.a.valid := false.B
    out.a.bits := DontCare

    dontTouch(out.a)
    dontTouch(out.d)

    // node.makeIOs()(ValName("client_port")) // ! This is not necessary ! if you doing so, make sure all the manually created IOs are well connected.
  }

  // or you can write in this pattern
  /*
    lazy val module = new LazyModuleImp(this) {
  
    }
  */

  // LazyModuleImp can be extended with trait
  /*
    lazy val module = new Impl
    class Impl extends LazyModuleImp(this) with HasPerfLogging {

    }
  */


}

class TestManagerModule()(implicit p: Parameters) extends LazyModule {

  // 1. Create node
  //    ManagerNode is a kind of SinkNode, output signal is not required
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
        responseFields = Nil,
        requestKeys = Nil
      ),      
    )
  )

  // 2. Create real hardware
  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    node.in.foreach{ in =>
      println("(1) [clients]", in._2.client.clients)

      in._2.client.clients.zipWithIndex.foreach{
        case (client, i) => println("(2) [others]", i, client.name, client.nodePath)
      }
    }
    println()

    // SinkNode has no output (bundle, edge)
    assert(node.out == Nil)

    // node.in ==> Seq[(BI, EI)] 
    //              BI: Bundle Input ==> class TLBundleA/B/C/D/E ==> real hardware bundle
    //              EI: Edge Input   ==> class TLEdgeIn         ==> edge is used to propagate and negotiate parameters between nodes 

    /*
      class TLEdgeIn(
        client:  TLClientPortParameters,
        manager: TLManagerPortParameters,
        params:  Parameters,
        sourceInfo: SourceInfo)
        extends TLEdge(client, manager, params, sourceInfo)
      {
        ...
      }
    */
    val (in, _) = node.in(0)


    // in.a := DontCare
    in.d := DontCare

    dontTouch(in.a)
    dontTouch(in.d)

    // node.makeIOs()(ValName("manager_port"))
  }

}

class TestTop()(implicit p: Parameters) extends LazyModule {

  // 1. Instantiate nodes
  val client = LazyModule(new TestClientModule())
  val manager = LazyModule(new TestManagerModule())

//  client.node
//  println("")

  // 2. Connect nodes
  manager.node := client.node

  // ! Node can also be created in top level
  //   In this scenario, client node is more like a port agent
  // val topClient = TLClientNode(Seq(
  //     TLMasterPortParameters.v1(
  //       clients =
  //         Seq(TLMasterParameters.v1(
  //             name = "test top client module"
  //         )),
  //       minLatency = 0,
  //       echoFields = Nil,
  //       requestFields = Nil,
  //       responseKeys = Nil
  //     )
  //   )
  // )
  // manager.node := topClient
  // topClient.makeIOs()(ValName("client_port"))

  // 3. Create real top hardware
  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {

    val io = IO(new Bundle{
      val val3 = Output(Bool())
    })

    // topClient.makeIOs()(ValName("client_port"))

    io.val3 := false.B

    dontTouch(io.val3)
  }
}

class TestTop_1()(implicit p: Parameters) extends LazyModule {

  // 1. Instantiate nodes
  val client = LazyModule(new TestClientModule(2))
  val manager = LazyModule(new TestManagerModule())

  val client_1 = LazyModule(new TestClientModule(1))
  val xbar = TLXbar()

  xbar := client.node
  xbar := client_1.node

  // 2. Connect nodes
  //  manager.node := client.node
  manager.node := xbar

  // 3. Create real top hardware
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
    FirtoolOption("--strip-debug-info"),
    FirtoolOption("--disable-annotation-unknown")
  ))

  val top_1 = DisableMonitors(p => LazyModule(new TestTop_1()(p)))(config)
  (new ChiselStage).execute(Array("--target", "verilog") ++ args, Seq(
    ChiselGeneratorAnnotation(() => top_1.module),
    FirtoolOption("--strip-debug-info"),
    FirtoolOption("--disable-annotation-unknown")
  ))

}