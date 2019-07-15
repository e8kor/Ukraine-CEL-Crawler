package com.e8kor.cvk.crawler.actor

import akka.actor.{Actor, ActorLogging, Props}
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.e8kor.cvk.crawler.model.Candidate
import doobie._
import doobie.util.transactor.Transactor
import doobie.util.transactor.Transactor.Aux
import doobie._
import doobie.implicits._
import cats.effect.IO
import cats.implicits._

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
                        Option[Int])

  def props(db: String, user: String, pass: String) = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

    val xa: Aux[IO, Unit] = Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      s"jdbc:postgresql:$db",
      user,
      pass
    )

    Props(new DatabaseWriter(xa)(cs))
  }
  sealed trait DatabaseWriterAction
  case class Write(candidates: Seq[Candidate]) extends DatabaseWriterAction

}
class DatabaseWriter(xa: Aux[IO, Unit])(implicit cs: ContextShift[IO])
    extends Actor
    with ActorLogging {
  import DatabaseWriter._

  def insert(cs: Seq[Candidate]): doobie.ConnectionIO[Int] = {
    val sql =
      """insert into
         |candidate (name, uri, association, number, photo, registration, details, attachments, references, hash) 
         |values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
    val data = cs.toList.flatMap(Candidate.unapply)
    Update[CandidateInfo](sql).updateMany(data)
  }

  override def receive: Receive = {
    case Write(candidates) =>
      insert(candidates).transact(xa).unsafeRunSync()

  }

}
