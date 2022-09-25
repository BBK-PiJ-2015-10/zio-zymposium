package zhubs

import zio._

object Zhubs extends ZIOAppDefault {

  // buffers between producer/consumer

  def producer(queue: Queue[Int]): ZIO[Any,Nothing,Unit] =
    ZIO.foreachDiscard(0 to 5){ i =>
      queue.offer(i) *> ZIO.sleep(100.milliseconds)
    }

  def consumer(queue: Queue[Int]): ZIO[Any,Nothing,Nothing] =
    queue.take.flatMap { i =>
      Console.printLine(s"Consumer got $i").!
    }.forever

  val queueExample = for {
    queue <- Queue.bounded[Int](16)
    producerFib       <- producer(queue).fork
    consumerFib       <- consumer(queue).fork
    _   <- producerFib.join
    _   <- ZIO.sleep(1.seconds)
    //_   <- consumerFib.join
    _   <- consumerFib.interrupt
    //_   <- producerFib.interrupt
  } yield ()

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = queueExample
}
