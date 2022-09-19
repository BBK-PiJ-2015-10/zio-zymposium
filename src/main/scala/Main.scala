
import zio._
import zio.stream._

import java.io.IOException
//import zio.Console.printLine

//Concept       ZIO 1     vs   ZIO 2
//Beginning    ZStream        ZStream
//Middle       Transducer     ZPipeline
//End          ZSink          ZSink

object Main extends ZIOAppDefault {

  val stream1 = ZStream
    .repeatZIO(ZIO.succeed("Hello, Zymposium"))
    .take(10)
  //.runDrain // runts until the stream completes
  //.runCollection  // ZIO[R,E,Chunk[A]]
  //.runFold
  //.runForeach { string => ZIO.debug(string}

  val stream2 = {
    ZStream
      .repeat(3)
      .map(_ * 2)
      .filter(_ % 3 == 0)
      .foreach {
        n => Console.printLine(n)
      }
  }

  val divisibleByThreesStream =
    ZStream.repeat(6)
      .map(_ * 2)
      .filter(_ % 3 == 0)
      .take(10)

  val divisibleByThreesStream2 =
    ZStream
      .repeatZIO(Random.nextIntBounded(99))
      .map(_ * 2)
      .filter(_ % 3 == 0)
      .tap(n => ZIO.debug(s"GOT N $n"))

  val runStream =
    divisibleByThreesStream >>> ZSink.collectAll[Int]

  val runStream2  =
    divisibleByThreesStream2 >>> ZSink.collectAllN[Int](10)

  // using pipes

  val sourcer = ZStream.repeatZIO(Random.nextIntBounded(99))

  val doubler: ZPipeline[Any, Nothing, Int, Int] = ZPipeline.map[Int,Int](_ * 2)

  val filter : ZPipeline[Any,Nothing,Int,Int] = ZPipeline.filter[Int](_ % 3 == 0)

  //val collectTen = ZSink

  val runViaPipes =
    sourcer >>> doubler >>> filter >>> ZSink.collectAllN[Int](10)

      //.repeatZIO(ZIO.succeed("Hello, Zymposium"))
    //.runDrain // runts until the stream completes
    //.runCollection  // ZIO[R,E,Chunk[A]]
    //.runFold
    //.runForeach { string => ZIO.debug(string}
  val testRunner: IO[IOException, Unit] = Console.printLine("alexis")

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
  runViaPipes.debug("Hello")

    //source https://www.youtube.com/watch?v=8hG_UY0Dazw
  //left on 20:17

  }


}