package zhubs

import zio._

object Zhubs extends ZIOAppDefault {

  // buffers between producer/consumer

  def producer(queue: Queue[Int]): ZIO[Any, Nothing, Unit] =
    ZIO.foreachDiscard(0 to 5) { i =>
      queue.offer(i) *> ZIO.sleep(100.milliseconds)
    }

  def consumer(queue: Queue[Int]): ZIO[Any, Nothing, Nothing] =
    queue.take.flatMap { i =>
      Console.printLine(s"Consumer got $i").!
    }.forever

  def consumer(label: String)(queue: Queue[Int]): ZIO[Any, Nothing, Nothing] =
    queue.take.flatMap { i =>
      Console.printLine(s"Consumer with id: $label got $i").!
    }.forever

  val queueExample = for {
    queue <- Queue.bounded[Int](16)
    producerFib <- producer(queue).fork
    consumerFib <- consumer(queue).fork
    _ <- producerFib.join
    _ <- ZIO.sleep(1.seconds)
    _ <- consumerFib.interrupt
  } yield ()

  val queueExampleTwoConsumers = for {
    queue <- Queue.bounded[Int](16)
    producerFib <- producer(queue).fork
    consumerFib1 <- consumer("A")(queue).fork
    consumerFib2 <- consumer("B")(queue).fork
    _ <- producerFib.join
    _ <- ZIO.sleep(1.seconds)
    _ <- consumerFib1.interrupt
    _ <- consumerFib2.interrupt
  } yield ()

  /*
     Queues => Work Distribution
     Hubs   => Work Broadcast
   */

  final case class NaiveHub[A](queues: Chunk[Queue[A]]) {

    def offer(a: A): ZIO[Any,Nothing,Unit] =
      ZIO.foreachDiscard(queues)(_.offer(a))

    def take(n: Int): ZIO[Any,Nothing,A] = queues(n).take

  }

  object NaiveHub {

    def make[A](n: Int): ZIO[Any,Nothing,NaiveHub[A]] =
      ZIO.foreach(Chunk.fromIterable(0 until n))(i => Queue.bounded[A](16))
        .map(qs => NaiveHub(qs))
  }

  def naiveHubProducer(hub: NaiveHub[Int]): ZIO[Any, Nothing, Unit] =
    ZIO.foreachDiscard(0 to 5) { i =>
      hub.offer(i) *> ZIO.sleep(100.milliseconds)
    }

  def naiveHubConsumer(label: String,id: Int)(hub: NaiveHub[Int]): ZIO[Any, Nothing, Nothing] =
    hub.take(id).flatMap { i =>
      Console.printLine(s"Consumer with label: $label got $i").!
    }.forever


  val queueExampleWithNaiveHub = for {
    hub <- NaiveHub.make[Int](2)
    producerFib <- naiveHubProducer(hub).fork
    consumerFib1 <- naiveHubConsumer("logging framework",0)(hub).fork
    consumerFib2 <- naiveHubConsumer("database",1)(hub).fork
    _ <- producerFib.join
    _ <- ZIO.sleep(1.seconds)
    _ <- consumerFib1.interrupt
    _ <- consumerFib2.interrupt
  } yield ()



  def hubProducer(hub: Hub[Int]): ZIO[Any, Nothing, Unit] =
    ZIO.foreachDiscard(0 to 5) { i =>
      hub.publish(i) *> ZIO.sleep(100.milliseconds)
    }

  def hubConsumer(label: String)(hub: Hub[Int]) = {
    val subscription = for {
      deQueue <- hub.subscribe
      article <- deQueue.take
      _       <- Console.printLine(s"$label got $article").!
    } yield ()
    subscription.forever
  }


  lazy val queueExampleWithHub = for {
    hub <- Hub.bounded[Int](16)
    _ <- hubConsumer("logging framework")(hub).fork
    _ <- hubConsumer("database")(hub).fork
    _ <- hubProducer(hub).fork
    _ <- ZIO.sleep(1.seconds)
  } yield ()

  def hubConsumerWithCounter(label: String)(hub: Hub[Int])(currentSubscribers: Ref[Int],start: Promise[Nothing,Unit]) = {
    val subscription = for {
      deQueue <- hub.subscribe
      n       <- currentSubscribers.updateAndGet(_ + 1)
      _       <-  if (n == 4) start.succeed(()) else ZIO.unit
      article <- deQueue.take
      _       <- Console.printLine(s"$label got $article").!
    } yield ()
    subscription.forever
  }

  lazy val queueExampleWithHubWithCounter = for {
    hub <- Hub.bounded[Int](16)
    ref <- Ref.make(0)
    promise <- Promise.make[Nothing,Unit]
    _ <- hubConsumerWithCounter("logging framework")(hub)(ref,promise).fork
    _ <- hubConsumerWithCounter("database")(hub)(ref,promise).fork
    _ <- hubConsumerWithCounter("data eager")(hub)(ref,promise).fork
    _ <- hubConsumerWithCounter("info destroyer")(hub)(ref,promise).fork
    _ <- promise.await
    _ <- hubProducer(hub).fork
    _ <- ZIO.sleep(1.seconds)
  } yield ()


  /*
     Naive Hub
     Queue1   | |2|3|4|5|
     Queue2   | | |3|4|5|
   */

  /*
     ZIO Hub
     Array | |2|3|4|5|
     consumer1   index == 1
     consumer2   index == 2
   */


  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = queueExampleWithHubWithCounter

  //left on minute  40:17

}