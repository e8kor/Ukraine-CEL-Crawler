package com.e8kor.cvk.crawler.actor

import java.time.LocalDateTime
import java.util.Date

import akka.actor.{Actor, ActorLogging, Props}
import cats.effect.{Blocker, ContextShift, IO}
import cats.implicits._
import com.e8kor.cvk.crawler.model.Candidate
import doobie._
import doobie.util.transactor.Transactor
import doobie.util.transactor.Transactor.Aux
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.pgisimplicits._
import doobie._
import doobie.implicits._
import doobie.util.ExecutionContexts
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import com.zaxxer.hikari.HikariDataSource
import doobie.hikari.HikariTransactor

import scala.concurrent.ExecutionContext

object DatabaseWriter {

  type CandidateInfo = (String,
                        String,
                        String,
                        Int,
                        Option[String],
                        String,
                        String,
                        Map[String, String],
                        Map[String, String],
                        Option[Int],
    Date)

  def props(host: String = "candidates",
            user: String = "postgres",
            pass: String = ""): Props = {
    Props(new DatabaseWriter())
  }
  sealed trait DatabaseWriterAction
  case class Persist(candidates: Seq[Candidate]) extends DatabaseWriterAction
  case object Done extends DatabaseWriterAction

}

class DatabaseWriter() extends Actor with ActorLogging {
  import DatabaseWriter._
  implicit var cs: ContextShift[IO] = _
  implicit var xa: HikariTransactor[IO] = _

  override def preStart(): Unit = {
    cs = IO.contextShift(ExecutionContext.global)
    val config = context.system.settings.config
    val dataSource = new HikariDataSource
    dataSource.setJdbcUrl(config.getString("db.url"))
    dataSource.setUsername(config.getString("db.user"))
    dataSource.setPassword(config.getString("db.password"))
    dataSource.setDriverClassName(config.getString("db.driverClassName"))
    xa = HikariTransactor[IO](
      dataSource,
      ExecutionContext.global,
      Blocker.liftExecutionContext(ExecutionContext.global)
    )
  }

  def insert(cs: Seq[Candidate]): doobie.ConnectionIO[Int] = {
    val sql =
      """insert into
         |cvk.candidates (name, uri, association, pos, photo, registration, details, attachments, refs, hash, created)
         |values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?)""".stripMargin
    val data = cs.toList.flatMap(Candidate.unapply)

    Update[DatabaseWriter.CandidateInfo](sql).updateMany(data)
  }

  override def receive: Receive = {
    case Persist(candidates) =>
      log.debug(s"received command to persist ${candidates.length} candidates")
      insert(candidates).transact(xa).unsafeRunSync()

  }

}
