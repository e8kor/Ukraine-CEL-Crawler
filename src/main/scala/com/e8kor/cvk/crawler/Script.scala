package com.e8kor.cvk.crawler

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.util.Timeout
import com.e8kor.cvk.crawler.actor.Crawler

object Script {
  import akka.pattern._
  def main(args: Array[String]): Unit = {
    val system = ActorSystem(s"cvk-system")
    import system.dispatcher
    val root = system.settings.config.getString("app.root")
    val path = system.settings.config.getString("app.path")
    implicit val timeout: Timeout = Timeout(
      system.settings.config.getDuration("app.timeout", TimeUnit.SECONDS),
      TimeUnit.SECONDS
    )
    val app = new Application(system, root, path)
    app.crawler ? Crawler.Tick(root, path)
  }

}
