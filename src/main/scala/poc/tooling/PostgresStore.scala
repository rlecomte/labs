package poc.tooling

import cats.effect.IO

class PostgresStore[F: Sync] extends Store[F] {}
