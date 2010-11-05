import sbt._

class BenchmarkProject(info: ProjectInfo) extends DefaultProject(info) {

  lazy val jansi_repo = MavenRepository("jansi","http://jansi.fusesource.org/repo/release")
  lazy val smx_repo = MavenRepository("smx","http://svn.apache.org/repos/asf/servicemix/m2-repo")
  
  lazy val scalate = "org.apache.karaf.shell" % "org.apache.karaf.shell.console" % "2.1.0"
  lazy val slf4j_nop = "org.slf4j" % "slf4j-nop" % "1.6.0"

}

