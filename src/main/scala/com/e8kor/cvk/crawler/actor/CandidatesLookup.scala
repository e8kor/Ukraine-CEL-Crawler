package com.e8kor.cvk.crawler.actor

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import com.e8kor.cvk.crawler.model.Candidate
import net.ruippeixotog.scalascraper.browser._

object CandidatesLookup {

  def props(browser: Browser = JsoupBrowser()): Props = {
    Props(new CandidatesLookup(browser))
  }

  sealed trait CandidatesAction
  case class Pull(root: String, uri: String) extends CandidatesAction
  case class Response(candidates: Seq[Candidate]) extends CandidatesAction
}

class CandidatesLookup private (browser: Browser)
    extends Actor
    with ActorLogging {

  import net.ruippeixotog.scalascraper.dsl.DSL._
  import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
  import CandidatesLookup._

  override def receive: Receive = {
    case Pull(root, uri) =>
      val s = sender()
      val pattern =
        context.system.settings.config.getString("candidates.pattern")
      val doc = browser.get(root + uri)
      val candidates = for {
        raw <- (doc >> elementList(pattern)) filter (_.text.trim.length > 1)
        _ = log.debug(s"parsing $raw")
        uri = raw.attr("href")
        _ = log.debug(s"adding task: $uri")
        ref = context.actorOf(CandidateLookup.props(), uri)
      } yield ref -> uri
      val tasks = candidates // take 1
      context.become(waiting(tasks, Seq.empty, s))
      tasks.foreach {
        case (r, uri) => r ! CandidateLookup.Pull(root, uri)
      }
  }

  def waiting(todo: Seq[(ActorRef, String)],
              done: Seq[Candidate],
              leader: ActorRef): Receive = {
    case CandidateLookup.Response(data) =>
      log.debug(s"received task response: $data")
      val s = sender()
      s ! PoisonPill
      val task = todo.find(_._1 == s)
      task match {
        case Some(t) =>
          val tasks = todo.filter(_ != t)
          val completed = done :+ data
          if (tasks.isEmpty) {
            context.become(receive)
            leader ! Response(completed)
          } else {
            context.become(waiting(tasks, completed, leader))
          }
        case None =>
          log.warning(s"message from $s is $data")
      }
  }
}
