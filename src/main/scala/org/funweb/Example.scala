package org.funweb

import java.io.File
import org.mortbay.jetty.Server

object ExampleServer {
  def continueExample: (Request) => Response = {request => new HtmlResponse("<html><body>woot!</body></html>")}
  
  def main(args: Array[String]) {
    val router = new Router(
      (FixedRoute("GET", "/"), (request: Request) => new HtmlResponse("<html><body>homepage</body></html>"))
      :: (RegexRoute("GET", "/person/([A-Za-z0-9]+)/profile", List("username")), (request: Request) => new TextResponse("hello, " + request.routeParam("username")))
      :: (FixedRoute("GET", "/README"), (request: Request) => new FileResponse(new File("README")))
      :: (FixedRoute("GET", "/continue"), (request: Request) => new ContinueResponse(continueExample))
      :: Nil
    )

    val server = new Server(9999);
    server.setHandler(new FunwebJetty6Handler(router));
    server.start();
  }
}