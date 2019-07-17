package com.e8kor.cvk.crawler

import akka.actor.{ActorRef, ActorSystem}
import akka.event.{Logging, LoggingAdapter}
import com.e8kor.cvk.crawler.actor.Crawler

import scala.util.parsing.combinator.RegexParsers
import scala.annotation.tailrec
import scala.io.StdIn

object REPL {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem(s"cvk-system")
    val root = "https://www.cvk.gov.ua/pls/vnd2019/"
    val path = "wp401pt001f01=919.html"
    val app = new Application(system, root, path)
    app.commandLoop()
  }

}
