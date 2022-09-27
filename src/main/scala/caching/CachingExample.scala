package caching

import zio._

import java.time.Instant

//Source: https://www.youtube.com/watch?v=uv0kyCCfB1Q&list=PLvdARMfvom9C8ss18he1P5vOcogawm5uC&index=39
object CachingExample extends ZIOAppDefault {

  final case class AuthToken(value: Long, expiration: Instant) {
    def isExpired(now: Instant): Boolean = expiration.isBefore(now)
  }

  case class SlackClient(ref: Ref[Set[AuthToken]]){
    def refreshToken: ZIO[Any, Nothing, AuthToken] =
      for {
        _  <-  ZIO.logInfo(s"Getting refresh token")
          .delay(Duration.fromMillis(1000))
          .repeat(Schedule.recurs(3))
        now <- Clock.instant
        long <- Random.nextLong
        token = AuthToken(long,now.plusSeconds(4))
        _    <- ref.update(_.filterNot(_.isExpired(now)) + token)
      } yield token

  }



  val effect: Task[Unit] = ZIO.succeed(println("ale"))
  val repeatingSchedule: ZIO[Any,Throwable,Long] = effect.repeat(Schedule.recurs(2))


  val example = for {
    tokens   <- Ref.make[Set[AuthToken]](Set())
    other = AuthToken(200L,Instant.now())
    _   <- tokens.update(set => set + other )
    _              <- SlackClient(tokens).refreshToken
  } yield()




  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    example
}
