import sbt._

class BenchmarkProject(info: ProjectInfo) extends DefaultProject(info) {
  lazy val mavenLocal = "Local Maven Repository" at "file://" + Path.userHome + "/.m2/repository"
  lazy val jansi_repo = MavenRepository("jansi","http://jansi.fusesource.org/repo/release")
  lazy val smx_repo = MavenRepository("smx","http://svn.apache.org/repos/asf/servicemix/m2-repo")
  lazy val fusesource_snapshot_repo = MavenRepository("fusesource-snapshot","http://repo.fusesource.com/nexus/content/repositories/snapshots/")
  lazy val fusesource_public_repo = MavenRepository("fusesource-public","http://repo.fusesource.com/nexus/content/repositories/public/")
  
  lazy val karaf_console = "org.apache.karaf.shell" % "org.apache.karaf.shell.console" % "2.2.1"
  lazy val slf4j_nop = "org.slf4j" % "slf4j-nop" % "1.6.0"
  lazy val hawtdispatch = "org.fusesource.hawtdispatch" % "hawtdispatch-scala" % "1.8"
  lazy val stompjms = "org.fusesource.stompjms" % "stompjms-client" % "1.8"

}

