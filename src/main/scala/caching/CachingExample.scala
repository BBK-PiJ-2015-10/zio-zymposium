package caching

import zio._

import java.time.Instant

object CachingExample extends ZIOAppDefault {

  final case class AuthToken(value: Long, expiration: Instant) {
    def isExpried(now: Instant): Boolean = expiration.isBefore(now)
  }

  case class SlackClient(ref: Ref[Set[AuthToken]]){
    def refreshToken =
      for {
        _  <-   ZIO.logInfo(s"Getting refresh token $ref")
        //_  <  Console.print(".").delay(100.millis).repeatN(10).!

      } yield ()

  }


  val example = for {
    tokens   <- Ref.make[Set[AuthToken]](Set())
    other = AuthToken(200L,Instant.now())
    _   <- tokens.update(set => set + other )
    _              <- SlackClient(tokens).refreshToken
  } yield()



  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = example
}
