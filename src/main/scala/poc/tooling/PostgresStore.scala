package poc.tooling

import cats.effect.IO
import io.circe.Decoder
import io.circe.Json
import io.circe.Encoder
import io.circe.syntax._
import java.{util => ju}
import java.time.ZonedDateTime
import cats.effect.Sync
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.syntax.ConnectionIOOps
import doobie.util.transactor.Transactor
import cats.data.NonEmptyList
import cats.Applicative

class PostgresStore[F[_]: Sync](transactor: Transactor[F]) extends Store[F] {

  def getAggregateEvents[A](id: AggregateId)(implicit
      decoder: Decoder[A]
  ): fs2.Stream[F, Event[A]] = {
    PostgresStore.selectAggregateEvents(id).transact(transactor)
  }

  def getAll(
      seqNum: Long,
      eventTypes: Seq[String]
  ): fs2.Stream[F, Event[Json]] = ???

  def register[A](
      id: AggregateId,
      version: Option[Version],
      events: List[NewEvent[A]]
  )(implicit
      encoder: Encoder[A]
  ): F[Boolean] = {
    PostgresStore.insertEvents(id, version, events).transact(transactor)
  }
}

object PostgresStore {
  import doobie.implicits.javatime._
  import doobie.postgres.circe.json.implicits._
  import doobie.postgres.implicits._

  implicit val eventMetadataGet: Get[EventMetadata] =
    Get[Json].temap(_.as[EventMetadata].leftMap(_.toString()))

  implicit val eventMetadataPut: Put[EventMetadata] =
    Put[Json].contramap(_.asJson)

  implicit val aggregateIdMeta: Meta[AggregateId] =
    Meta[ju.UUID].imap(AggregateId.apply)(_.value)

  implicit val seqNumMeta: Meta[SeqNum] =
    Meta[Long].imap(SeqNum.apply)(_.value)

  implicit val versionMeta: Meta[Version] =
    Meta[Long].imap(Version.apply)(_.value)

  def selectAggregateEvents[A](
      id: AggregateId
  )(implicit decoder: Decoder[A]): fs2.Stream[ConnectionIO, Event[A]] = {
    val F = Sync[ConnectionIO]

    sql"""
      SELECT stream_id, seq_num, version, aggregate_type, event_type, payload, metadata, created_at
      FROM events
      WHERE stream_id=$id 
      ORDER BY seq_num ASC
    """
      .query[Event[Json]]
      .stream
      .evalMap[ConnectionIO, Event[A]] {
        case e =>
          e.traverse[Decoder.Result, A](_.as[A])
            .fold(
              err => F.raiseError(UnexpectedError(err.toString)),
              F.pure(_)
            )
      }
  }

  def insertEvents[A](
      id: AggregateId,
      version: Option[Version],
      newEvents: List[NewEvent[A]]
  )(implicit encoder: Encoder[A]) = {
    val F = Sync[ConnectionIO]

    val newVersion = Version(version.map(_.value).getOrElse(1L) + 1L)

    val getCurrentVersion =
      sql"""SELECT version 
              FROM events 
              WHERE stream_id=$id
        """.query[Version].option

    val insertEventsSql: String = """
      INSERT INTO (stream_id, 
                   aggregate_type, 
                   event_type, 
                   payload, 
                   metadata, 
                   version) 
      VALUES (?, ?, ?, ?, ?, ?)
    """

    val tuples = newEvents.map { e =>
      (
        e.id,
        e.aggregateType,
        e.eventType,
        e.payload.asJson,
        e.metadata,
        newVersion
      )
    }

    val insertEvents: ConnectionIO[Int] =
      Update[(AggregateId, String, String, Json, EventMetadata, Version)](
        insertEventsSql
      ).updateMany(tuples)

    val insertNewVersion = sql"""
        INSERT INTO events (version) 
        VALUES ($newVersion)
        WHERE stream_id=$id
    """.update.run

    for {
      currentVersion <- getCurrentVersion
      isSameVersion = currentVersion == version
      _ <-
        if (isSameVersion) {
          insertEvents >> insertNewVersion
        } else {
          F.pure(())
        }
    } yield isSameVersion
  }
}
