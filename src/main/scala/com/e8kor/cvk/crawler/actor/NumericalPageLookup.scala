package com.e8kor.cvk.crawler.actor

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import com.e8kor.cvk.crawler.model.Candidate
import net.ruippeixotog.scalascraper.browser._

object NumericalPageLookup {

  def props(browser: Browser = JsoupBrowser()): Props = {
    Props(new NumericalPageLookup(browser))
  }

  sealed trait PageAction
  case class Pull(root: String, uri: String) extends PageAction
  case class Response(candidates: Seq[Candidate]) extends PageAction
}

class NumericalPageLookup private (browser: Browser)
    extends Actor
    with ActorLogging {

  import net.ruippeixotog.scalascraper.dsl.DSL._
  import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
  import NumericalPageLookup._

  override def receive: Receive = {
    case Pull(root, uri) =>
      val s = sender()
      val pattern = context.system.settings.config.getString("numerical.pattern")
      val head = context.actorOf(CandidatesLookup.props(), uri) -> uri
      val tail = for {
        raw <- browser.get(root + uri) >> elementList(pattern)
        _ = log.debug(s"parsing $raw")
        uri = raw.attr("href")
        _ = log.debug(s"adding task: $uri")
        ref = context.actorOf(CandidatesLookup.props(), uri)
      } yield ref -> uri
      val tasks = head +: tail // take 1
      context.become(waiting(tasks, Seq.empty, s))
      tasks.foreach {
        case (r, uri) => r ! CandidatesLookup.Pull(root, uri)
      }
  }

  def waiting(todo: Seq[(ActorRef, String)],
              done: Seq[Candidate],
              leader: ActorRef): Receive = {
    case CandidatesLookup.Response(data) =>
      log.debug(s"received task response: $data")
      val s = sender()
      s ! PoisonPill
      val task = todo.find(_._1 == s)
      task match {
        case Some(t) =>
          val tasks = todo.filter(_ == t)
          val completed = done ++ data
          if (tasks.isEmpty) {
            leader ! Response(completed)
          } else {
            context.become(waiting(tasks, completed, leader))
          }
        case None =>
          log.warning(s"message from $s is $data")
      }
  }
}
