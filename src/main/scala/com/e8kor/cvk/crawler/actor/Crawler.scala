package com.e8kor.cvk.crawler.actor

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}

object Crawler {

  val props = Props(new Crawler)

  sealed trait CrawlerAction
  case class Tick(root: String, uri: String) extends CrawlerAction
}

class Crawler extends Actor with ActorLogging {

  import Crawler._

  override def receive: Receive = {
    case Tick(root, uri) =>
      context.become(waiting)
      context.actorOf(AlphabeticalPageLookup.props(), uri) ! AlphabeticalPageLookup
        .Pull(root, uri)
  }

  def waiting: Receive = {
    case AlphabeticalPageLookup.Response(candidates) =>
      log.debug("job done")
      sender() ! PoisonPill
      log.debug("persist please")
      context.actorOf(DatabaseWriter.props(), "writer") ! DatabaseWriter.Persist(candidates)
    case DatabaseWriter.Done =>
      context.unbecome()
      sender() ! PoisonPill
  }

}
