require 'buildr/scala'

repositories.remote << "http://www.ibiblio.org/maven2/"

JETTY6 = group('jetty', 'jetty-util', :under=>'org.mortbay.jetty', :version=>'6.1.3')
JETTY7 = group('jetty-server', 'jetty-util', 'jetty-continuation', 'jetty-io', 'jetty-http', :under=>'org.eclipse.jetty', :version=>'7.0.0.RC6')
SERVLET = group('servlet-api', :under=>'javax.servlet', :version=>'2.5')

Project.local_task :run

define 'funweb' do
  project.version = '0.1' 
  project.group = 'org.funweb'

  task :run => :compile do
    Java::Commands.java('org.funweb.ExampleServer', :classpath => compile.dependencies + [compile.target.to_s])
  end

  test.using(:scalatest)
  compile.with JETTY6, JETTY7, SERVLET
  package :jar
end
