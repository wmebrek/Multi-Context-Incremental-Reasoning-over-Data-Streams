name := "sma_processing"

version := "1.0"

scalaVersion := "2.12.5"

cancelable in Global := true

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"
libraryDependencies += "com.github.scopt" %% "scopt" % "3.5.0"
libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.5"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

libraryDependencies += "com.google.guava" % "guava" % "19.0"
/*libraryDependencies += "eu.larkc.csparql" % "CSPARQL-engine" % "0.9.6" from "http://streamreasoning.org/maven"
libraryDependencies += "eu.larkc.csparql" % "CSPARQL-common" % "0.9.6" from "http://streamreasoning.org/maven/eu/larkc/csparql/csparql-common/0.9.6/csparql-common-0.9.6.jar"
libraryDependencies += "eu.larkc.csparql" % "csparql-core" % "0.9.6" from "http://streamreasoning.org/maven/eu/larkc/csparql/csparql-core/0.9.6/csparql-core-0.9.6.jar"
libraryDependencies += "eu.larkc.csparql" % "csparql-cep-api" % "0.9.6" from "http://streamreasoning.org/maven/eu/larkc/csparql/csparql-cep-api/0.9.6/csparql-cep-api-0.9.6.jar"
libraryDependencies += "eu.larkc.csparql" % "csparql-cep-esper" % "0.9.6" from "http://streamreasoning.org/maven/eu/larkc/csparql/csparql-cep-esper/0.9.6/csparql-cep-esper-0.9.6.jar"
libraryDependencies += "eu.larkc.csparql" % "csparql-sparql-jena" % "0.9.6" from "http://streamreasoning.org/maven/eu/larkc/csparql/csparql-sparql-jena/0.9.6/csparql-sparql-jena-0.9.6.jar"
libraryDependencies += "eu.larkc.csparql" % "csparql-sparql-sesame" % "0.9.6" from "http://streamreasoning.org/maven/eu/larkc/csparql/csparql-sparql-sesame/0.9.6/csparql-sparql-sesame-0.9.6.jar"
libraryDependencies += "eu.larkc.csparql" % "csparql-sparql-api" % "0.9.6" from "http://streamreasoning.org/maven/eu/larkc/csparql/csparql-sparql-api/0.9.6/csparql-sparql-api-0.9.6.jar"
*/
libraryDependencies += "com.espertech" % "esper" % "3.5.0"
libraryDependencies += "org.apache.jena" % "jena-tdb" % "1.0.1"

libraryDependencies += "org.apache.jena" % "jena-core" % "2.13.0"
libraryDependencies += "commons-lang" % "commons-lang" % "2.6"
libraryDependencies += "com.nativelibs4java" % "bridj" % "0.7.0"
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.5"
libraryDependencies += "org.slf4j" % "slf4j-log4j12" % "1.7.5"
libraryDependencies +=
  "log4j" % "log4j" % "1.2.15" excludeAll(
    ExclusionRule(organization = "com.sun.jdmk"),
    ExclusionRule(organization = "com.sun.jmx"),
    ExclusionRule(organization = "javax.jms")
  )

resolvers += "reasonning repo" at "http://streamreasoning.org/maven/"
resolvers += "maven central" at "https://repo1.maven.org/maven2/"
//resolvers += "Jade tilab" at "https://jade.tilab.com/maven/"

scalacOptions += "-feature"
scalacOptions += "-deprecation"
scalacOptions += "-language:postfixOps"
scalacOptions += "-language:implicitConversions"

lazy val commonSettings = Seq(
  test in assembly := {}
)

lazy val ticker = (project in file(".")).
  settings(commonSettings: _*).
  configs(IntegrationTest).
  settings(Defaults.itSettings: _*).
  settings(
    mainClass in assembly := Some("Program"),
    test in assembly := {}
  )

/*
lazy val disseval = (project in file(".")).
  settings(commonSettings: _*).
  configs(IntegrationTest).
  settings(Defaults.itSettings: _*).
  settings(
    assemblyJarName in assembly := "disseval.jar",
    mainClass in assembly := Some("evaluation.diss.DissEvalMain"),
    test in assembly := {}
  )
*/

//
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case _ => MergeStrategy.first
}
excludeFilter in assembly ~= {
  exclude => exclude && FileFilter.globFilter("src/test/**/*.*")
}
