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


  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = partitionsEamples

  //left on 24:36 https://www.youtube.com/watch?v=3EO0yVf63xI


}
