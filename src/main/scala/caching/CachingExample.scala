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

    def refreshToken: ZIO[Any, IOException, AuthToken] =
      for {
        _    <- Console.print("Getting fresh token")
        _    <- Console.print(".").delay(Duration.fromMillis(100)).repeatN(10)
        _    <- Console.print("\n")
//        _  <-  ZIO.logInfo(s"Getting refresh token")
//          .delay(Duration.fromMillis(100))
//          .repeat(Schedule.recurs(10))
        now <- Clock.instant
        long <- Random.nextLong
        expiration = now.plusSeconds(4)
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

    def refreshToken: ZIO[SlackClient, IOException, AuthToken] =
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

    val dog : MyState[String] = ???

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
    } yield ref.modify {
        case Empty =>  for {
          a  <- zio
          deadline = getDeadline(a)
          newSate = Value(a,deadline)
        } yield (a,newSate)
        case  Value(a,deadline) =>  ???
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


  val example = for {
    tokens   <- Ref.make[Set[AuthToken]](Set())
    other = AuthToken(200L,Instant.now())
    _   <- tokens.update(set => set + other )
    _              <- SlackClient(tokens).refreshToken
  } yield()



  // left on 29.19

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    example2CachedRun
}
