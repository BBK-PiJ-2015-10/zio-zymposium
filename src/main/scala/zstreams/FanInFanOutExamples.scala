package zstreams

import zio._
import zio.stream._
import zstreams.FanInFanOutExamples.Event.{NotificationEvent, SecurityEvent}

//session 4

/*
reactive-marbles
    fan in pattern
    stream1
                stream3
    stream2

    fan out pattern
              stream2
    stream1
              stream3
 */

/*
   Fan-out options:
      a. broadcast -> send all inputs to all downstreams
      b. groupBy -> send inputs to downstreams on some key
 */



object FanInFanOutExamples extends ZIOAppDefault{

  val stream1 : ZStream[Any,Nothing,Int] = ZStream.fromIterable(1 to 100)

  val stream2: ZStream[Any, Nothing, Int] = stream1.groupByKey(_ % 2 == 0){
    case (isEven,stream) =>
      if (isEven) stream.map(_ * 2).debug("even")
      else stream.map(_ * 3).debug("odd")
  }

  sealed trait Event

  object Event {
    final case class SecurityEvent(severity: Int) extends Event
    final case class NotificationEvent(description: String) extends Event
  }


  val eventStream: ZStream[Any, Nothing, Event] =
    ZStream(SecurityEvent(9000),NotificationEvent("hello"),SecurityEvent(3000),NotificationEvent("ciao"))

  def processSecurityEvent(in: ZStream[Any,Nothing,SecurityEvent])  =
    in.map(_.severity).debug("SECURITY")

 def processNotificationEvent(in: ZStream[Any,Nothing,NotificationEvent]) =
   in.map(_.description).debug("NOTIFICATION")


 val partitions: ZIO[Any with Scope, Nothing, (ZStream[Any, Nothing, SecurityEvent], ZStream[Any, Nothing, NotificationEvent])] =
   eventStream.partitionEither( {
     case sec @ SecurityEvent(_) => ZIO.succeed(Left(sec))
     case not @NotificationEvent(_) => ZIO.succeed(Right(not))
   })

  val partitionsEamples =
    partitions.flatMap{
      case (securityEvents,notificationEvents) =>
        processSecurityEvent(securityEvents).runDrain zipPar
          processNotificationEvent(notificationEvents).runDrain
    }

  /*           stream2
      stream1             stream4
               stream3
   */
  val runFanInAFanOut =
    partitions.flatMap {
      case (securityEvents,notificationEvents) =>
        val processedSecurityEvents = processSecurityEvent(securityEvents).map(Left(_))
        val processedNotificationEvents = processNotificationEvent(notificationEvents).map(Right(_))
        val merged: ZStream[Any, Nothing, Either[Int, String]] = processedSecurityEvents.merge(processedNotificationEvents)
        merged.tap{
          ale => ZIO.logInfo(s"CULON $ale")
        }.runDrain
    }

  val runFanInAFanOutEx2: ZStream[Any, Nothing, Either[Int, String]] = {
    ZStream.unwrapScoped(
    partitions.map {
    case (securityEvents, notificationEvents) =>
      val processedSecurityEvents = processSecurityEvent(securityEvents).map(Left(_))
      val processedNotificationEvents = processNotificationEvent(notificationEvents).map(Right(_))
      processedSecurityEvents.merge(processedNotificationEvents)
  })
  }

  /*
    Variations of fan-in:
    merge - pull from both upstreams as quickly as they have stuff and emit unchanged
     RED,          BLUE
         RED,GREEN
    merge
    RED,RED,GREEN,BLUE
    zip / zipWith - pairwise combine them, pull from both upstreams and emit one value once you have a value from both
    [RED,RED][BLUE,GREEN]
    zipWithLatest - pairwise combine them, hold on to the last value seen from each side, emit new value
    (RED,RED) (RED,GREEN) (BLUE,GREEN)
    interleave
    //aggregateAsynchWithin

   */

  /*
    ZIO combinations
     zip
     zipPar
     race
     orElse
   */

  sealed trait Marble

  object Marble {
    case object Red extends Marble
    case object Green extends Marble
    case object Blue extends Marble
  }

  def delayedStream[A](values: (A,Duration)*): ZStream[Any,Nothing,A] = {
    values.toList match {
      case (value,duration) :: rest =>
      ZStream.fromZIO(ZIO.succeed(value).delay(duration)) ++ delayedStream(rest: _*)
      case _ => ZStream.empty
    }
  }

  val stream1Marbles = delayedStream(Marble.Red -> 1.second, Marble.Blue -> 3.seconds)
  val stream2Marbles = delayedStream(Marble.Red -> 2.seconds, Marble.Green -> 1.seconds)

  val mergeMarbleExample =
    stream1Marbles.merge(stream2Marbles).debug.runDrain

  val zipMarbleExample =
    stream1Marbles.zip(stream2Marbles).debug.runDrain

  val zipWithLatestMarbleExample =
    stream1Marbles.zipWithLatest(stream2Marbles)((_,_)).debug.runDrain


  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = zipWithLatestMarbleExample
    //runFanInAFanOutEx2.debug("Merged").runCollect.debug

  //left on 24:36 https://www.youtube.com/watch?v=3EO0yVf63xI


}
