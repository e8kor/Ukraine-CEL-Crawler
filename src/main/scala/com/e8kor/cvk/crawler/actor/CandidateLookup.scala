package com.e8kor.cvk.crawler.actor

import java.time.LocalDateTime
import java.util.Date

import akka.actor.{Actor, ActorLogging, Props}
import com.e8kor.cvk.crawler.model.Candidate
import net.ruippeixotog.scalascraper.browser._
import net.ruippeixotog.scalascraper.model.Element

import scala.util.hashing.MurmurHash3

object CandidateLookup {

  type Table = List[List[Element]]

  def props(browser: Browser = JsoupBrowser()): Props = {
    Props(new CandidateLookup(browser))
  }

  def newData(uri: String, hash: Int): Candidate =
    Candidate("", uri, "", 0, None, "", "", Map.empty, Map.empty, Some(hash), new Date())

  sealed trait CandidateAction
  case class Pull(root: String, uri: String) extends CandidateAction
  case class Response(candidate: Candidate) extends CandidateAction
}

class CandidateLookup private (browser: Browser)
    extends Actor
    with ActorLogging {

  import net.ruippeixotog.scalascraper.dsl.DSL._
  import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
  import CandidateLookup._

  def parseTable(candidateData: Candidate, table: Table): Candidate =
    table.foldLeft(candidateData) {
      case (acc, List(field, value, photo))
          if field.text contains "Висування" =>
        val photoLink =
          if (photo.hasAttr("src"))
            if (photo.attr("src") == "img2019/nopicture.png")
              None
            else Some(photo.attr("src"))
          else None
        acc.copy(association = value.text, photo = photoLink)
      case (acc, List(field, value))
          if field.text contains "№ у виборчому списку партії" =>
        val numberInList = Option(value.text).map(_.toInt).getOrElse(0)
        acc.copy(number = numberInList)
      case (acc, List(field, value))
          if field.text contains "Номер та дата рішення про реєстрацію кандидатом" =>
        acc.copy(registration = value.text)
      case (acc, List(field, value))
          if field.text contains "Відомості про кандидата" =>
        acc.copy(details = value.text)
      case (acc, List(field, value))
          if field.text contains "Перелік доданих матеріалів в електронному вигляді" =>
        val links = value.siblings
          .filter(x => x.hasAttr("href"))
          .map(x => x.text -> x.attr("href"))
          .toMap
        acc.copy(attachments = links)
      case (acc, List(field, value)) if field.text == "Інші посилання" =>
        val links = value.siblings
          .filter(x => x.hasAttr("href"))
          .map(x => x.text -> x.attr("href"))
          .toMap
        acc.copy(references = links)
      case (acc, other) =>
        log.warning(s"unexpected table row: $other")
        acc
    }

  override def receive: Receive = {
    case Pull(root, uri) =>
      log.debug(s"received task: $uri")
      val s = sender()

      val namePath: String = context.system.settings.config.getString("candidate.namePath")
      val tablePath: String = context.system.settings.config.getString("candidate.tablePath")
      val doc = browser.get(root + uri)
      val hash = MurmurHash3.stringHash(doc.toHtml)
      val acc = newData(uri, hash)
      val opt: Option[Candidate] = for {
        candidateName <- doc >?> text(namePath)
        _ = log.debug(s"candidate name: $candidateName")
        named = acc.copy(name = candidateName)
        tableContent = (doc >> elementList(tablePath)) >> elementList("td")
        _ = log.debug(s"table lookup data: $tableContent")
        entity = parseTable(named, tableContent)
        _ = log.debug(s"candidate data: $entity")
      } yield entity
      s ! Response(opt.getOrElse(acc))
  }
}
