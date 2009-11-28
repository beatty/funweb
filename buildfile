require 'buildr/scala'

JETTY = group('jetty', 'jetty-util', :under=>'org.mortbay.jetty', :version=>'6.1.3')
SERVLET = group('servlet-api', :under=>'javax.servlet', :version=>'2.5')

Project.local_task :run

define 'funweb' do
  project.version = '0.1' 
  project.group = 'org.funweb'

  task :run => :compile do
    Java::Commands.java('org.funweb.ExampleServer', :classpath => compile.dependencies + [compile.target.to_s])
  end

  test.using(:scalatest)
  compile.with JETTY, SERVLET
  package :jar
end
