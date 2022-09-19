
import zio._
import zio.stream._

import java.io.IOException
//import zio.Console.printLine

//Concept       ZIO 1     vs   ZIO 2
//Beginning    ZStream        ZStream    (Channel)
//Middle       Transducer     ZPipeline  (Channel)
//End          ZSink          ZSink      (Channel)

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

  val randomNums = ZStream.repeatZIO(Random.nextIntBounded(99))

  val doubler: ZPipeline[Any, Nothing, Int, Int] = ZPipeline.map[Int,Int](_ * 2)

  val filter : ZPipeline[Any,Nothing,Int,Int] = ZPipeline.filter[Int](_ % 3 == 0)

  val collectTen = ZSink.collectAllN[Int](10)

  val collectFive = ZSink.collectAllN[Int](5)

  val runViaPipes =
    randomNums >>> doubler >>> filter >>> collectTen

  val runViaPipes2 =
    randomNums >>> doubler >>> filter >>> collectFive


  val runViaPipes3 = randomNums.broadcast(2,16).flatMap { streams =>
    val subscriber1 = streams(0) >>> doubler >>> filter >>> collectFive
    val subscriber2 = streams(1) >>> doubler >>> filter >>> collectTen
    subscriber1 zipPar subscriber2
  }

  val runViaPipes4 =
    for {
    shared <- randomNums.broadcastDynamic(16)
    s1  = shared >>> doubler >>> filter >>> collectTen
    s2  = shared >>> doubler >>> filter >>> collectFive
      _  <- (s1 zipPar s2).debug("result")
  } yield ()


      //.repeatZIO(ZIO.succeed("Hello, Zymposium"))
    //.runDrain // runts until the stream completes
    //.runCollection  // ZIO[R,E,Chunk[A]]
    //.runFold
    //.runForeach { string => ZIO.debug(string}
  val testRunner: IO[IOException, Unit] = Console.printLine("alexis")

  //explanation of channel


  // emit zior or more values of type outElem
  // if it finishes at all is going to fiwth with exactly one OutErr or OutDone
  // accept zeio ore values of type InElem
  // patentially accept exactly one input of either InDone or InErr
  // OUTeLEM IS LEFT OVER
  trait MyZChannel[-Env,-InError,-InElem,-InDone,+OutErr, +OutElem,+OutDone]

  type MyZStream[-R,E,A] =               MyZChannel[R,Any,Any,Any,E,Chunk[A],Any]
  type MyZSink[-R,+E,-In,+L,+Z] =        MyZChannel[R,Nothing,In,Any,E,Chunk[L],Z]
  type MyZPipeline[-Env,+Err,-In,+Out] = MyZChannel[Env,Nothing,Chunk[In],Any,Err,Chunk[Out],Any]
  type MyZIO[-R,+E,+A] =                 MyZChannel[R,Any,Any,Any,E,Nothing,A]

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =  runViaPipes4.debug("ale")
  //{
//    for {
//     // _ <- runViaPipes.debug("RUN 1")
//      //_ <- runViaPipes2.debug("RUN 2")
//      _   <- runViaPipes4.debug("RUN 3")
//    } yield ()


 // runViaPipes.debug("mima") zipRight   runViaPipes2.debug("Hello")

    //source https://www.youtube.com/watch?v=8hG_UY0Dazw
  //left on 52:00

  //}


}