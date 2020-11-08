package poc

import cats.effect.Resource
import doobie.util.ExecutionContexts
import cats.effect.Blocker
import doobie.hikari.HikariTransactor
import cats.effect.IO
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import cats.effect.ContextShift
import poc.tooling.Store
import poc.tooling.PostgresStore

case class Deps[F[_]](store: Store[F], logger: Logger[F])
object Deps {

  // Resource yielding a transactor configured with a bounded connect EC and an unbounded
  // transaction EC. Everything will be closed and shut down cleanly after use.
  def mkTransactor(implicit
      cs: ContextShift[IO]
  ): Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
      be <- Blocker[IO] // our blocking EC
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        "jdbc:postgresql://localhost:5434/postgres",
        "postgres",
        "trololo",
        ce, // await connection here
        be // execute JDBC operations here
      )
    } yield xa

  val mkLogger = Slf4jLogger.create[IO]

  def deps()(implicit cs: ContextShift[IO]): Resource[IO, Deps[IO]] = {
    for {
      xa <- mkTransactor
      logger <- Resource.liftF(mkLogger)
    } yield Deps(new PostgresStore(xa), logger)
  }
}
