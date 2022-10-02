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

  val example1 = for {
    token <- SlackClient.refreshToken
    _     <- ZIO.sleep(4.seconds)
    _     <- SlackClient.postMessage("Hello, all!",token)
    _     <- SlackClient.postMessage("Zymposium rocks",token)
  } yield ()


  val example = for {
    tokens   <- Ref.make[Set[AuthToken]](Set())
    other = AuthToken(200L,Instant.now())
    _   <- tokens.update(set => set + other )
    _              <- SlackClient(tokens).refreshToken
  } yield()




  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    example1.provideLayer(SlackClient.live)
}
