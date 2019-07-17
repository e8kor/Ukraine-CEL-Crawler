name := "cvk-crawler"

version := "1.0"

scalaVersion := "2.12.6"

lazy val akkaVersion = "2.6.0-M4"

lazy val scrapperVersion = "2.1.0"
lazy val scalaParsersVersion = "1.1.1"
lazy val logbackVersion = "1.2.3"
lazy val posgresVersion = "0.8.0-M1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "net.ruippeixotog" %% "scala-scraper" % scrapperVersion,
  "org.scala-lang.modules" %% "scala-parser-combinators" % scalaParsersVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "org.tpolecat" %% "doobie-hikari" % posgresVersion,
  "org.tpolecat" %% "doobie-postgres" % posgresVersion,
  "org.tpolecat" %% "doobie-postgres-circe" % posgresVersion,
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)
scalacOptions += "-Ypartial-unification"
mainClass := Some("com.e8kor.cvk.crawler.Script")
assemblyJarName in assembly := "app.jar"
dockerfile in docker := {
  val artifact: File = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("openjdk:8-jre")
    add(artifact, artifactTargetPath)
    entryPoint("java", "-Dconfig.file=/app/config/application.conf", "-Dlogback.configurationFile=/app/config/logback.xml", "-jar", artifactTargetPath)
  }
}
enablePlugins(DockerPlugin, AssemblyPlugin)