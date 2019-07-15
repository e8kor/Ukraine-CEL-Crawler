package com.e8kor.cvk.crawler

import akka.actor.{ActorRef, ActorSystem}
import akka.event.{Logging, LoggingAdapter}
import com.e8kor.cvk.crawler.actor.Crawler

import scala.util.parsing.combinator.RegexParsers
import scala.annotation.tailrec
import scala.io.StdIn

object Application {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem(s"cvk-system")
    val root = "https://www.cvk.gov.ua/pls/vnd2019/"
    val path = "wp401pt001f01=919.html"
    val app = new Application(system, root, path)
    app.commandLoop()
  }

}

class Application(system: ActorSystem, root: String, path: String)
    extends Terminal {

  val log: LoggingAdapter = Logging(system, getClass.getName)
  val crawler: ActorRef = createCrawler()

  def createCrawler(): ActorRef = {
    system.actorOf(Crawler.props, "crawler")
  }

  @tailrec
  private def commandLoop(): Unit = {
    val commandString = StdIn.readLine()

    if (commandString == null) {
      system.terminate()
    } else {
      Command(commandString) match {
        case Command.Tick =>
          crawler ! Crawler.Tick(root, path)
          commandLoop()
        case Command.Quit =>
          system.terminate()
        case Command.Unknown(command) =>
          log.warning("Unknown command {}!", command)
          commandLoop()
      }
    }
  }
}

trait Terminal {

  protected sealed trait Command

  protected object Command {

    case object Tick extends Command

    case object Quit extends Command

    case class Unknown(command: String) extends Command

    def apply(command: String): Command =
      CommandParser.parseAsCommand(command)
  }

  private object CommandParser extends RegexParsers {

    def parseAsCommand(s: String): Command =
      parseAll(parser, s) match {
        case Success(command, _) => command
        case _                   => Command.Unknown(s)
      }

    def tick: Parser[Command.Tick.type] =
      "tick|t".r ^^ (_ => Command.Tick)

    def quit: Parser[Command.Quit.type] =
      "quit|q".r ^^ (_ => Command.Quit)

  }

  private val parser: CommandParser.Parser[Command] =
    CommandParser.tick | CommandParser.quit
}
