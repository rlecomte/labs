val scalaParserCombinatorsVersion = "1.1.2"
val catsVersion = "2.2.0"
val catsEffectVersion = "2.2.0"
val catsMtlVersion = "1.0.0"
val fs2Version = "2.4.4"
val log4catsVersion = "1.1.1"
val logbackVersion = "1.2.3"
val circeVersion = "0.13.0"
val doobieVersion = "0.9.2"
val http4sVersion = "0.21.8"

val ScalaVersion = "2.13.3"
val ScalacOptions = Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Xfatal-warnings",
  "-Ywarn-unused"
)
scalacOptions := ScalacOptions

val GlobalSettings = Seq(
  scalaVersion := ScalaVersion,
  scalacOptions := ScalacOptions,
  fork in run := true, // prevent leaks on sbt run
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
)

val CommonDependencies = Seq(
  "org.scala-lang.modules" %% "scala-parser-combinators" % scalaParserCombinatorsVersion,
  "org.typelevel" %% "cats-core" % catsVersion,
  "org.typelevel" %% "cats-effect" % catsEffectVersion,
  "co.fs2" %% "fs2-core" % fs2Version,
  "co.fs2" %% "fs2-io" % fs2Version,
  "org.typelevel" %% "cats-mtl" % catsMtlVersion,
  "io.chrisdavenport" %% "log4cats-core" % log4catsVersion,
  "io.chrisdavenport" %% "log4cats-slf4j" % log4catsVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "org.tpolecat" %% "doobie-core" % doobieVersion,
  "org.tpolecat" %% "doobie-hikari" % doobieVersion,
  "org.tpolecat" %% "doobie-postgres" % doobieVersion,
  "org.tpolecat" %% "doobie-postgres-circe" % doobieVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion
)

lazy val root = (project in file("."))
  .settings(
    organization := "io.rlecomte",
    name := "labs-all",
    version := "0.0.1-SNAPSHOT",
    libraryDependencies ++= CommonDependencies
  )
  .settings(GlobalSettings)
