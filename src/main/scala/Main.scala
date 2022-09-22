
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

  //val exampleOfMultipleErrors : ZStream[Any,Nothing,Either[MyError,MyValue]]


  val customDoubler: ZPipeline[Any, Nothing, Int, Int] = myMap[Int,Int](_ * 2)

  //val customTwiceDoubler: ZPipeline[Any, Nothing, Int, Int] = myMapTwice[Int,Int](_ * 2)

  val naturals = ZStream.unfold(0)(s => Some(s,s+1))

  val runStreamCustom =
    naturals.rechunk(2) >>> customDoubler >>> filter >>> collectFive

//  val runStreamCustom2 =
//    naturals.rechunk(2) >>> customTwiceDoubler >>> filter >>> collectFive

  //ZStream.fromZIO(ZIO.executor.debug)

  def myMap[In,Out](f: In => Out): ZPipeline[Any,Nothing,In,Out] = {

    lazy val read : ZChannel[Any,ZNothing,Chunk[In],Any,Nothing,Chunk[Out],Any] =
      ZChannel.readWith(
      (in: Chunk[In]) => {
        val out = in.map(f)
        println(s"[Read: $in, Write: $out]")
        ZChannel.write(out) *> read
      },
      (error: ZNothing) => ZChannel.fail(error),
      (done: Any) => ZChannel.succeed(done)
    )
    ZPipeline.fromChannel(read)
  }

  // Stream(1,3,4,5)
  //.scan(0)(_ + _)
  //Stream(0,1,4,8,13
  def myScan[In,Out](zero: Out)(f: (Out,In) => Out): ZPipeline[Any,Nothing,In,Out] = {

    def read(state: Out) : ZChannel[Any,ZNothing,Chunk[In],Any,Nothing,Chunk[Out],Any] =
      ZChannel.readWith(
        (in: Chunk[In]) => {
          val out  = in.scanLeft(state)(f)
          val updatedState = out.lastOption.getOrElse(state)
          println(s"[IN: $in \nOUT: $out \nSTATE: $state]")
          ZChannel.write(out.drop(1)) *> read(updatedState)
        },
        (error: ZNothing) => ZChannel.fail(error),
        (done: Any) => ZChannel.succeed(done)
      )
    ZPipeline.fromChannel(read(zero))
  }

  val initial = ZStream(1,3,4,5)

  val runStreams3 = initial.rechunk(2) >>> myScan[Int,Int](0)(_ + _) >>> collectFive


  //val fileWritesiNK: ZSink[Any,IOException,Byte,Nothing,Unit] = ???

  //Session 3

  sealed trait Sandwich

  object Sandwich {
    case object Calypso extends Sandwich
    case object CaptChicken extends Sandwich
    case object Jaws extends Sandwich
    case object PorkeysNightmare extends Sandwich
    case object Boring extends Sandwich
  }

  val sandwichMapping  =
  Map(
    "Calipso" -> Sandwich.Calypso,
    "CaptChick" -> Sandwich.CaptChicken,
    "Jaws" -> Sandwich.Jaws,
    "PorkeysNightmare" -> Sandwich.PorkeysNightmare,
     "Boring" -> Sandwich.Boring
  )

  def eat(sandwich: Sandwich): UIO[Unit] = ZIO.debug(s"YUM! A delicious: ${sandwich.toString}")


  val encodedSandwiches = Chunk.fromIterable(
    ("Calipso" + "PorkeysNightmare" + "Boring" + "Boring" +"Jaws" + "Jaws" + "DOGS")
  )

  val encodedSandwichStream : UStream[Char] = ZStream.fromChunk(encodedSandwiches)

  val encodedSandwichStream1 : UStream[Char] = encodedSandwichStream.rechunk(3)

  val sandwichDecodingPipeline: ZPipeline[Any,Nothing,Char,Sandwich] = {

    // read some input
    // decode as many sandwich as we can, maybe getting some left over input
    // emit the sandwiches decoded
    // read more input and added it to the left overs
    // repeat

    def read(buffer: Chunk[Char]): ZChannel[Any,ZNothing,Chunk[Char],Any,Nothing,Chunk[Sandwich],Any] =
      ZChannel.readWith(
        (in: Chunk[Char]) => {
          val (leftOvers, sandwiches) = processBuffer(buffer,in)
          ZChannel.writeAll(sandwiches) *> read(leftOvers)
        },
        (error: ZNothing) => ZChannel.fail(error),
        (done: Any) =>
          ZChannel
          .fromZIO(ZIO.debug(s"WARNING: SANDWICH FRAGMENT ${buffer.mkString} LOST! HUNGER MAY ENSURE ")
            .when(buffer.nonEmpty))
          *>
          ZChannel.succeed(done)
      )

    ZPipeline.fromChannel(read(Chunk.empty))

  }

  // Chunk[JawsBO]
  // Chunk[ringBoringBoring]
  // -> (Chunk[BO],Chunk[Jaws])
  // -> (Chunk[CA],(Chunk[Boring],Chunk[Boring],Chunk[Boring]))
  private def processBuffer(buffer: Chunk[Char],input: Chunk[Char]): (Chunk[Char],Chunk[Sandwich]) = {
     val combined = buffer ++ input
     val iterator = combined.iterator
     val builder= ChunkBuilder.make[Sandwich]()
     var candidate = ""
     while (iterator.hasNext){
       val char = iterator.next()
       candidate += char
       sandwichMapping.get(candidate) match {
         case Some(sandwich) => {
           builder += sandwich
           candidate = ""
         }
         case None => ()
       }
     }
    (Chunk.fromIterable(candidate),builder.result())
  }

  val decodedSandwichStream: UStream[Sandwich] =
    encodedSandwichStream1.chunksWith(_.debug) >>> sandwichDecodingPipeline

  val runSandwichStream: ZIO[Any, Nothing, Chunk[Sandwich]] =
    decodedSandwichStream.tap(eat(_)).runCollect.debug

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = Console.printLine("ALEXIS") *>
    runSandwichStream

  //{
//    for {
//     // _ <- runViaPipes.debug("RUN 1")
//      //_ <- runViaPipes2.debug("RUN 2")
//      _   <- runViaPipes4.debug("RUN 3")
//    } yield ()


 // runViaPipes.debug("mima") zipRight   runViaPipes2.debug("Hello")

    //source https://www.youtube.com/watch?v=8hG_UY0Dazw part 2
  //left on 33:30

  //}


}