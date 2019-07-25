package com.e8kor.cvk.crawler.actor

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import akka.actor.{Actor, ActorLogging, Props}
import com.e8kor.cvk.crawler.model.{Candidate, CandidateAux, CandidateDetails}
import net.ruippeixotog.scalascraper.browser._
import net.ruippeixotog.scalascraper.model.Element

import scala.collection.mutable
import scala.util.hashing.MurmurHash3

object CandidateLookup {

  type Table = List[List[Element]]

  def props(browser: Browser = JsoupBrowser()): Props = {
    Props(new CandidateLookup(browser))
  }

  sealed trait CandidateAction
  case class Pull(root: String, uri: String) extends CandidateAction
  case class Response(candidate: Candidate) extends CandidateAction

  val locale: Locale =
    Locale.getAvailableLocales.find(x => x.getCountry == "UA").orNull
  def birthDateParser = new SimpleDateFormat("dd MMMM yyyy", locale)
  def registrationParser = new SimpleDateFormat("dd.MM.yyyy", locale)
}

class CandidateLookup private (browser: Browser)
    extends Actor
    with ActorLogging {

  import net.ruippeixotog.scalascraper.dsl.DSL._
  import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
  import CandidateLookup._

  private def getPhoto(field: Element): Option[String] = {
    if (field.hasAttr("src"))
      if (field.attr("src") == "img2019/nopicture.png")
        None
      else Some(field.attr("src"))
    else None
  }

  private def getAssociation(field: Element): Option[String] = {
    Option(field.text).filterNot(_.isEmpty).map(_.trim().toLowerCase)
  }

  private def getElectionRegion(field: Element): Option[Int] = {
    Option(field.text).filterNot(_.isEmpty).map(_.trim().toInt)
  }

  private def getNumberInList(field: Element): Option[Int] = {
    Option(field.text).filterNot(_.isEmpty).map(_.trim().toInt)
  }

  private def getNumberAndRegistrationDate(field: Element): (Int, Date) = {
    Option(field.text).filterNot(_.isEmpty).map(_.split(" ").toList) match {
      case Some(num :: _ :: date :: Nil) =>
        num.toInt -> registrationParser.parse(date)
    }
  }

  private def getReferences(field: Element): Map[String, String] = {
    field.siblings
      .filter(x => x.hasAttr("href"))
      .map(x => x.text.toLowerCase -> x.attr("href"))
      .toMap
  }

  private def parse(candidateData: Candidate, table: Table): Candidate =
    table.foldLeft(candidateData) {
      case (acc, List(field, value, photo))
          if field.text contains "виборчий округ" =>
        acc.copy(
          photo = getPhoto(photo),
          electionRegion = getElectionRegion(value)
        )
      case (acc, List(field, value, photo))
          if field.text.contains("исування") =>
        acc.copy(
          partyAssociation = getAssociation(value),
          photo = getPhoto(photo)
        )

      case (acc, List(field, value)) if field.text.contains("исування") =>
        acc.copy(partyAssociation = getAssociation(value))

      case (acc, List(field, value))
          if field.text.contains("Номер та дата реєстрації документів в ЦВК") =>
        //ignore
        acc
      case (acc, List(field, value))
          if field.text contains "№ у виборчому списку партії" =>
        acc.copy(partyListNum = getNumberInList(value))

      case (acc, List(field, value))
          if field.text contains "Номер та дата рішення про реєстрацію кандидатом" =>
        val (num, date) = getNumberAndRegistrationDate(value)
        acc.copy(registrationNum = num, registrationDate = date)

      case (acc, List(field, value))
          if field.text contains "Відомості про кандидата" =>
        withDetails(acc, value)

      case (acc, List(field, value))
          if field.text contains "Перелік доданих матеріалів в електронному вигляді" =>
        acc.copy(attachment = getReferences(value))

      case (acc, List(field, value)) if field.text == "Інші посилання" =>
        acc.copy(reference = getReferences(value))

      case (acc, List(field, value)) =>
        acc.copy(notParsed = acc.notParsed + (field.text -> value.text))

      case (acc, Nil) =>
        acc
    }

  private def getGender(items: mutable.Buffer[String]): Boolean = {
    items.exists(x => x.contains("народився"))
  }

  val countiesOfOrigin = Seq(
    "російська федерація",
    "республіка бурятія",
    "республіка вірменія",
    "росія",
    "республіка узбекистан",
    "ар крим",
    "сша",
    "республіка дагестан"

  )

  private def getGenderKeywords(gender: Boolean): Seq[String] = {
    if (gender) {
      Seq(
        "народився",
        "безпартійний",
        "член",
        "освіта",
        "проживає",
        "пенсіон",
        "безробітний",
        "тимчасово не працює",
        "громадянин",
        "протягом останніх п’яти років проживає на території україни",
        "судимість відсутня",
        "суб’єкт висування",
        "включений до виборчого списку під №",
        "відсутні відомості щодо місця проживання",
        "самовисування",
        "народний депутат України",
        "безроб"
      )
    } else {
      Seq(
        "народилася",
        "безпартійнa",
        "член",
        "освіта",
        "проживає",
        "пенсіон",
        "безробітна",
        "тимчасово не працює",
        "громадянка",
        "протягом останніх п’яти років проживає на території україни",
        "судимість відсутня",
        "суб’єкт висування",
        "включена до виборчого списку під №",
        "відсутні відомості щодо місця проживання",
        "самовисування",
        "народний депутат України",
        "безроб"
      )
    }
  }

  private def getBirthDate(items: mutable.Buffer[String],
                           keywords: Seq[String]): Date = {

    val item = items.find(x => x.contains(keywords(0)))
    item.foreach { x =>
      var next = items(items.indexOf(x) + 1)
      while (!next.trim.contains(" ")) {
        items.remove(items.indexOf(next))
        next = items(items.indexOf(x) + 1)
      }
      items.remove(items.indexOf(x))
    }

    val birthday = item.toList
      .flatMap(_.split(" ").toList.slice(1, 4)) match {
      case day :: month :: year :: _ =>
        val normalized = month.replaceAll("i", "і")
        birthDateParser.parse(s"$day $normalized $year")
    }
    birthday
  }

  private def getPoliticalMembership(items: mutable.Buffer[String],
                                     keywords: Seq[String]): Option[String] = {
    val item = items
      .find(x => x.contains(keywords(1)))
      .orElse(items.find(x => x.contains(keywords(2))))

    item.foreach { x =>
      items.remove(items.indexOf(x))
    }

    item
      .map(_.trim())
      .orElse(
        items
          .find(x => x.contains(keywords(14)))
          .map(_ => keywords(1))
      )
  }

//  private def getEducationIndex(items: mutable.Buffer[String],
//                                keywords: Seq[String]): Int = {
//    items.indexWhere(x => x.contains(keywords(3)))
//  }

  private def getEducation(items: mutable.Buffer[String],
                           keywords: Seq[String]): Option[String] = {
    val item = items.find(x => x.contains(keywords(3)))
    item.foreach { x =>
      items.remove(items.indexOf(x))
    }
    item.map(_.replaceAll(keywords(3), "").trim())
  }

  private def getCity(items: mutable.Buffer[String],
                      keywords: Seq[String]): Option[String] = {

    val item = items.find(x => x.contains(keywords(4)))
    item.foreach { x =>
      items.remove(items.indexOf(x))
    }
    item
      .map(_.replaceAll(keywords(4), "").trim())
      .orElse(items.find(x => x.contains(keywords(13))))
  }

  private def isCanApply(items: mutable.Buffer[String],
                         keywords: Seq[String]): Boolean = {
    val c1 = {
      val item = items.find(x => x.contains(keywords(8)))
      item.foreach(x => items.remove(items.indexOf(x)))
//      item.foreach { x =>
//        0 to items.indexOf(x) foreach items.remove
//      }
      item.isDefined
    }
    val c2 = {
      val item = items.find(x => x.contains(keywords(9)))
      item.foreach { x =>
        items.remove(items.indexOf(x))
      }
      item.isDefined
    }
    val c3 = {
      val item = items.find(x => x.contains(keywords(10)))
      item.foreach { x =>
        items.remove(items.indexOf(x))
      }
      item.isDefined
    }
    c1 && c2 && c3
  }

  private def getEmploymentData(
    items: mutable.Buffer[String],
    keywords: Seq[String]
  ): (String, Option[String]) = {
    items
      .find(
        x =>
          x.contains(keywords(5)) ||
            x.contains(keywords(6)) ||
            x.contains(keywords(7)) ||
            x.contains(keywords(15)) ||
            x.contains(keywords(16))
      )
      .map { x =>
        items.remove(items.indexOf(x))
        x.trim() -> None
      }
      .getOrElse {
        items.toList match {
          case employment :: employer :: _ =>
            items.remove(items.indexOf(employment))
            items.remove(items.indexOf(employer))
            employment.trim() -> Some(employer.trim())
          case employment :: _ =>
            items.remove(items.indexOf(employment))
            employment.trim() -> None
        }
      }
  }

  private def getSubject(items: mutable.Buffer[String],
                         keywords: Seq[String]): Option[String] = {
    val item = items
      .find(x => x.contains(keywords(11)))
      .orElse(items.find(x => x.contains(keywords(14))))

    item.foreach { x =>
      items.remove(items.indexOf(x))
    }
    item.map(_.replaceAll(keywords(11), "").replace("–", "").trim())
  }

  private def getMembershipNumber(items: mutable.Buffer[String],
                                  keywords: Seq[String]): Option[Int] = {
    val item = items.find(x => x.contains(keywords(12)))
    item.foreach { x =>
      items.remove(items.indexOf(x))
    }
    item
      .map(_.replaceAll(keywords(12), "").replace(".", "").trim)
      .map(_.toInt)
  }

  def withDetails(field: Element): CandidateDetails = {
    val items: mutable.Buffer[String] = Option(field.text)
      .filterNot(_.isEmpty)
      .map(_.toLowerCase)
      .map(_.split(",").map(_.trim).toList) match {
      case Some(xs) => xs.toBuffer
    }

    val gender = getGender(items)
    val keywords = getGenderKeywords(gender)
    val birthday = getBirthDate(items, keywords)
    val canApply = isCanApply(items, keywords)
    val politicalMembership =
      getPoliticalMembership(items, keywords).map(identity).get

    val city = getCity(items, keywords).map(identity).get

    val subject = getSubject(items, keywords).map(identity)
    val memNum = getMembershipNumber(items, keywords).map(identity)

    val education = getEducation(items, keywords).map(identity).get
    val (employment, employer) =
      getEmploymentData(items, keywords)

    val notParsedDetails = items.map(_.trim()).filterNot(_.isEmpty).toList

    CandidateDetails(
      gender = gender,
      birthday = birthday,
      canApply = canApply,
      party = politicalMembership,
      partyNum = memNum,
      education = education,
      employment = employment,
      employer = employer,
      city = city,
      subject = subject,
      notParsedDetails = notParsedDetails
    )

  }

  override def receive: Receive = {
    case Pull(root, uri) =>
      log.debug(s"received task: $uri")
      val s = sender()

      val namePath: String =
        context.system.settings.config.getString("candidate.namePath")
      val tablePath: String =
        context.system.settings.config.getString("candidate.tablePath")
      val rowPath: String =
        context.system.settings.config.getString("candidate.rowPath")
      val doc = browser.get(root + uri)
      val candidateName = doc >> text(namePath)
      val tableContent: Element = doc >> element(tablePath)
      val A = tableContent >> elementList(rowPath) >> elementList("td")

      val hash = MurmurHash3.stringHash(tableContent.innerHtml)
      val minimal = CandidateAux.`new`(uri, candidateName, hash)
      val entity = parse(minimal, A)

      log.debug(s"candidate data: $entity")
      s ! Response(entity)
  }
}
