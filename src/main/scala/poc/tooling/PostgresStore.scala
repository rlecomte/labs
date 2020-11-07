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

class PostgresStore[F[_]: Sync](transactor: Transactor[F]) extends Store[F] {

  def getAggregateEvents[A](id: AggregateId)(implicit
      decoder: Decoder[A]
  ): fs2.Stream[F, Event[A]] = {
    PostgresStore.selectAggregateEvents(id).transact(transactor)
  }

  def getAggregateEventFromVersion[A](id: AggregateId, version: Version)(
      implicit decoder: Decoder[A]
  ): F[Option[Event[A]]] = {
    PostgresStore.selectEventFromVersion(id, version).transact(transactor)
  }

  def getAll[A](
      seqNum: SeqNum,
      eventTypes: NonEmptyList[String]
  )(implicit encoder: Decoder[A]): fs2.Stream[F, Event[A]] = {
    PostgresStore.selectAllEvents(seqNum, eventTypes).transact(transactor)
  }

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

    val newVersion = version.map(_.inc()).getOrElse(Version.init)

    val getCurrentVersion =
      sql"""SELECT version
              FROM streams
              WHERE stream_id=$id
        """.query[Version].option

    val insertEventsSql: String = """
      INSERT INTO events(stream_id,
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
        INSERT INTO streams(stream_id, version)
        VALUES ($id, $newVersion)
    """.update.run

    for {
      currentVersion <- getCurrentVersion
      isSameVersion = currentVersion == version
      _ <-
        if (isSameVersion) {
          insertNewVersion >> insertEvents
        } else {
          F.pure(())
        }
    } yield isSameVersion
  }

  def selectAllEvents[A](
      seqNum: SeqNum,
      eventTypes: NonEmptyList[String]
  )(implicit decoder: Decoder[A]): fs2.Stream[ConnectionIO, Event[A]] = {
    val F = Sync[ConnectionIO]

    val q = fr"""
      SELECT stream_id, seq_num, version, aggregate_type, event_type, payload, metadata, created_at
      FROM events
      WHERE seq_num > $seqNum AND """ ++ Fragments.in(
      fr"event_type",
      eventTypes
    )

    q.query[Event[Json]]
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

  def selectEventFromVersion[A](
      id: AggregateId,
      version: Version
  )(implicit decoder: Decoder[A]): ConnectionIO[Option[Event[A]]] = {
    val F = Sync[ConnectionIO]

    sql"""
      SELECT stream_id, seq_num, version, aggregate_type, event_type, payload, metadata, created_at
      FROM events
      WHERE version = $version AND stream_id = $id
    """
      .query[Event[Json]]
      .option
      .flatMap {
        case Some(e) =>
          e.traverse[Decoder.Result, A](_.as[A])
            .fold(
              err => F.raiseError(UnexpectedError(err.toString)),
              v => F.pure(Some(v))
            )
        case None => F.pure(None)
      }
  }
}
