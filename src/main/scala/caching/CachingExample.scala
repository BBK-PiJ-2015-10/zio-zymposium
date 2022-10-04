package caching

import zio._

import java.io.IOException
import java.time.Instant
import scala.concurrent.duration.{Deadline, FiniteDuration}

//Source: https://www.youtube.com/watch?v=uv0kyCCfB1Q&list=PLvdARMfvom9C8ss18he1P5vOcogawm5uC&index=39
object CachingExample extends ZIOAppDefault {

  final case class AuthToken(value: Long, expiration: Instant) {
    def isExpired(now: Instant): Boolean = expiration.isBefore(now)
  }

  case class SlackClient(ref: Ref[Set[AuthToken]]){

    def refreshToken: ZIO[Any, Nothing, AuthToken] =
      for {
        _    <- Console.print("Getting fresh token").orDie
        _    <- Console.print(".").delay(Duration.fromMillis(100)).repeatN(10).orDie
        _    <- Console.print("\n").orDie
//        _  <-  ZIO.logInfo(s"Getting refresh token")
//          .delay(Duration.fromMillis(100))
//          .repeat(Schedule.recurs(10))
        now <- Clock.instant
        long <- Random.nextLong
        //expiration = now.plusSeconds(4)
        expiration = now.plusSeconds(7)
        token = AuthToken(long,expiration)
        _    <- ref.update(_.filterNot(_.isExpired(now)) + token)
      } yield token

    def postMessage(message: String,token: AuthToken): Task[Unit] = for {
      now    <- Clock.instant
      tokens <- ref.get
      isInvalid = !tokens(token) || token.isExpired(now)
      _    <- ZIO.fail(new Error("Invalid auth token")).when(isInvalid)
      _    <- ZIO.debug(s"#zio: ${scala.Console.CYAN}$message  ${scala.Console.RESET}")
    } yield()

  }

  object SlackClient {

    def refreshToken: ZIO[SlackClient, Nothing, AuthToken] =
      ZIO.serviceWithZIO[SlackClient](_.refreshToken)

    def postMessage(message: String,token: AuthToken): ZIO[SlackClient,Throwable,Unit] =
      ZIO.serviceWithZIO[SlackClient](_.postMessage(message, token))

    def live: ULayer[SlackClient] =
      ZLayer {
        for {
          ref <- Ref.make(Set.empty[AuthToken])
        } yield SlackClient(ref)
      }

  }


  sealed trait MyState[+A]
  case class Value[A](value: A, deadline: Instant) extends MyState[A]
  case object Empty extends MyState[Nothing]

  case class MyDeadline(deadline: Instant) {
    def isExpired: ZIO[Any, Nothing, Boolean] = Clock.instant.map(_.isAfter(deadline))
  }

  object DeadlineExample {

    val underlying: Deadline = ???

    trait ZDeadline {

      def timeLeft: UIO[FiniteDuration] = ZIO.succeed(underlying.timeLeft)


    }


  }

  object Utilities {

    // Ref
    // Ref we can perform pure updated within the modify function

    // Ref.Synchronized
    // Builds in a semaphore
    //Let us perform ZIO operations in the update function
    // Other writers have to semanticallly block while updating

    // TRef?
    def cached[R,E,A](zio: ZIO[R,E,A])(getDeadline: A => Instant) : UIO[ZIO[R,E,A]] =
      for {
      ref <- Ref.Synchronized.make[MyState[A]](Empty)
      refresh = for {
        _  <- ZIO.debug("STATE IS EMPTY. GETTING VALUE")
        a  <- zio
        deadline = getDeadline(a)
        newSate = Value(a,deadline)
      } yield (a,newSate)
        // ref.modify returns a tuple
    } yield ref.modifyZIO {
        case Empty => refresh
        case state @ Value(a,deadline) =>
          Clock.instant.flatMap { now =>
            if (now.isBefore(deadline)) ZIO.succeed((a,state))
            else refresh
          }
      }


    def cachedEager[R,E,A](zio: ZIO[R,E,A])(refreshDeadline: A => Instant): ZIO[R with Scope, E,ZIO[R,E,A]] = {

      def loop(ref: Ref[A],deadline: Instant): ZIO[R, E, Unit] = for {
        now <- Clock.instant
        duration = now.until(deadline,java.time.temporal.ChronoUnit.MILLIS)
        _    <- ZIO.sleep(duration.millis)
        //duration = Duration.fromInterval(now,deadline)
        //_    <- ZIO.sleep(duration)
        a     <- zio
        _    <- ref.set(a)
        deadline = refreshDeadline(a)
        _    <- loop(ref, deadline)
      } yield()

      for {
         a  <- zio
         deadline = refreshDeadline(a)
          ref  <- Ref.make[A](a)
         fiber <- loop(ref, deadline).forkScoped
      } yield ref.get

    }

  }

  case class CachedSlackClient(cachedGet: Task[AuthToken], slackClient: SlackClient) {

    def sentMessage(message: String): Task[Unit] =
      for {
        token <- cachedGet
        _   <- slackClient.postMessage(message,token)
      } yield ()

  }


  object CachedSlackClient {

    def sendMessage(message: String) =
      ZIO.serviceWithZIO[CachedSlackClient](_.sentMessage(message))

    val live = ZLayer.scoped {
      for {
      client <- ZIO.service[SlackClient]
      cachedToken <- Utilities.cachedEager(client.refreshToken)(_.expiration.minusSeconds(2))
    } yield CachedSlackClient(cachedToken,client)
    }

  }

  val example1NonCached = for {
    token <- SlackClient.refreshToken
    _     <- ZIO.sleep(4.seconds)
    _     <- SlackClient.postMessage("Hello, all!",token)
    _     <- SlackClient.postMessage("Zymposium rocks",token)
  } yield ()

  val example2CachedRun = { for {
    cachedToken <- SlackClient.refreshToken.cached(4.seconds)
    token <- cachedToken
    _     <- SlackClient.postMessage("Hello, world!",token)
    _     <- ZIO.sleep(2.seconds)
    token <- cachedToken
    _     <- SlackClient.postMessage("Wellcome to Zymposium",token)
    _     <- ZIO.sleep(2.seconds)
    token <- cachedToken
    _     <- SlackClient.postMessage("Woo!",token)
  } yield ()}.provide(SlackClient.live)

  def loop(getToken: ZIO[SlackClient,Nothing,AuthToken]): ZIO[SlackClient,Throwable,Unit] =
    for {
      token <- getToken
      _     <- SlackClient.postMessage(s"I love my token '$token'",token)
      sleep <- Random.nextIntBetween(1,3)
      _     <- ZIO.sleep(sleep.seconds)
      _     <- loop(getToken)
    } yield ()


  val exampleWithMyCachedRun = { for {
    cachedToken <- Utilities.cached(SlackClient.refreshToken)(_.expiration.minusMillis(200))
    _           <- loop(cachedToken).onInterrupt(ZIO.debug("HELP!")).fork
    _           <- Console.readLine
  } yield ()}.provide(SlackClient.live)

  val exampleWithMyCachedParellelRun = { for {
    cachedToken <- Utilities.cached(SlackClient.refreshToken)(_.expiration.minusMillis(200))
    _           <- ZIO.foreachPar(1 to 10)(n => loop(cachedToken)).onInterrupt(ZIO.debug("Help!")).fork
    _           <- Console.readLine
  } yield ()}
    .provide(SlackClient.live)


  val exampleWithMyCachedEacherParellelRun = { for {
    cachedToken <- Utilities.cachedEager(SlackClient.refreshToken)(_.expiration.minusSeconds(2))
    _           <- ZIO.foreachPar(1 to 10)(n => loop(cachedToken)).onInterrupt(ZIO.debug("Help!")).fork
    _           <- Console.readLine
  } yield ()}
    .provide(SlackClient.live,Scope.default)

  def loop2: ZIO[CachedSlackClient,Throwable,Unit] =
    for {
      _     <- CachedSlackClient.sendMessage("I love not having a token")
      sleep <- Random.nextIntBetween(1,3)
      _     <- ZIO.sleep(sleep.seconds)
      _     <- loop2
    } yield ()


  val exampleWithMyCachedEacherParellelRunP2 = { for {
    _           <- ZIO.foreachPar(1 to 10)(n => loop2).onInterrupt(ZIO.debug("Help!")).fork
    _           <- Console.readLine
  } yield ()}
    .provide(CachedSlackClient.live,SlackClient.live)


  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    exampleWithMyCachedEacherParellelRunP2
}
