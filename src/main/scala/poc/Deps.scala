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
import cats.~>

case class Deps[F[_]](store: Store[F], logger: Logger[F]) {
  def mapK[G[_]](f: F ~> G): Deps[G] = {
    Deps(store.mapK(f), logger.mapK(f))
  }
}

object Deps {

  // Resource yielding a transactor configured with a bounded connect EC and an unbounded
  // transaction EC. Everything will be closed and shut down cleanly after use.
  def mkTransactor(config: Config)(implicit
      cs: ContextShift[IO]
  ): Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
      be <- Blocker[IO] // our blocking EC
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        s"jdbc:postgresql://${config.postgresStoreHost}:${config.postgresStorePort}/${config.postgresStoreDb}",
        config.postgresStoreUser,
        config.postgresStorePassword,
        ce, // await connection here
        be // execute JDBC operations here
      )
    } yield xa

  val mkLogger = Slf4jLogger.create[IO]

  def deps(
      config: Config
  )(implicit cs: ContextShift[IO]): Resource[IO, Deps[IO]] = {
    for {
      xa <- mkTransactor(config)
      logger <- Resource.liftF(mkLogger)
    } yield Deps(new PostgresStore(xa), logger)
  }
}
