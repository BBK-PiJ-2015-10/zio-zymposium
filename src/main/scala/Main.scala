
import zio._
import zio.stream._

import java.io.IOException
//import zio.Console.printLine

//Concept       ZIO 1     vs   ZIO 2
//Beginning    ZStream        ZStream
//Middle       Transducer     ZPipeline
//End          ZSink          ZSink

object Main extends ZIOAppDefault {

  val stream: ZStream[Any, IOException, Unit] =
    ZStream.repeatZIO(Console.printLine("Hello, Zymposium"))
      .take(10)

  val testRunner: IO[IOException, Unit] = Console.printLine("alexis")

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    stream.runDrain.debug("RESULT")
}