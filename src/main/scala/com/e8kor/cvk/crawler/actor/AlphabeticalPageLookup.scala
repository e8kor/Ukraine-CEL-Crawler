package com.e8kor.cvk.crawler.actor

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import com.e8kor.cvk.crawler.model.Candidate
import net.ruippeixotog.scalascraper.browser._

object AlphabeticalPageLookup {

  def props(browser: Browser = JsoupBrowser()): Props = {
    Props(new AlphabeticalPageLookup(browser))
  }

  sealed trait AlphabeticalPageAction
  case class Pull(root: String, uri: String) extends AlphabeticalPageAction
  case class Response(candidates: Seq[Candidate]) extends AlphabeticalPageAction
}

class AlphabeticalPageLookup private (browser: Browser)
    extends Actor
    with ActorLogging {

  import net.ruippeixotog.scalascraper.dsl.DSL._
  import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
  import AlphabeticalPageLookup._

  override def receive: Receive = {
    case Pull(root, uri) =>
      val s = sender()

      val pattern = context.system.settings.config.getString("alpha.pattern")
      val head = context.actorOf(NumericalPageLookup.props(), uri) -> uri
      val tail = for {
        raw <- (browser.get(root + uri) >> elementList(pattern)) takeWhile (_.text.trim.length == 1)
        _ = log.debug(s"parsing $raw")
        uri = raw >> attr("href")("a")
        _ = log.debug(s"adding task: $uri")
        ref = context.actorOf(NumericalPageLookup.props(), uri)
      } yield ref -> uri
      val tasks = head +: tail // take 1
      context.become(waiting(tasks, Seq.empty, s))
      tasks.foreach {
        case (r, uri) => r ! NumericalPageLookup.Pull(root, uri)
      }
  }

  def waiting(todo: Seq[(ActorRef, String)],
              done: Seq[Candidate],
              leader: ActorRef): Receive = {

    case NumericalPageLookup.Response(data) =>
      log.debug(s"received task response: $data")
      val s = sender()
      s ! PoisonPill
      val task = todo.find(_._1 == s)
      task match {
        case Some(t) =>
          val tasks = todo.filter(_ != t)
          val completed = done ++ data
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
