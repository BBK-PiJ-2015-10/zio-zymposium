package caching

import zio._

import java.io.IOException
import java.time.Instant

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

  object Utilities {

    // Ref
    // Ref we can perform pure updated within the modify function

    // Ref.Synchronized
    // Builds in a semaphore
    //Let us perform ZIO operations in the update function
    // Other writers have to semanticallly block while updating

    // TRef?
    //
    def cached[R,E,A](zio: ZIO[R,E,A])(getDeadline: A => Instant) : UIO[ZIO[R,E,A]] =
      for {
      ref <- Ref.Synchronized.make[MyState[A]](Empty)
      refresh = for {
        _  <- ZIO.debug("STATE IS EMPTY. GETTING VALUE")
        a  <- zio
        deadline = getDeadline(a)
        newSate = Value(a,deadline)
      } yield (a,newSate)
    } yield ref.modifyZIO {
        case Empty => refresh
        case state @ Value(a,deadline) =>
          Clock.instant.flatMap { now =>
            if (now.isBefore(deadline)) ZIO.succeed((a,state))
            else refresh
          }
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

  // left on 48.389

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    exampleWithMyCachedParellelRun
}
