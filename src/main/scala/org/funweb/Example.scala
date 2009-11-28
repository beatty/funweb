package org.funweb

import java.io.File
import org.mortbay.jetty.Server

object ExampleServer {
  def main(args: Array[String]) {
    val router = new Router(
      (FixedRoute("GET", "/"), (request: Request) => new HtmlResponse("<html><body>homepage</body></html>"))
      :: (RegexRoute("GET", "/person/([A-Za-z0-9]+)/profile", List("username")), (request: Request) => new TextResponse("hello, " + request.routeParam("username")))
      :: (FixedRoute("GET", "/README"), (request: Request) => new FileResponse(new File("tronada/README")))
      :: Nil
    )

    val server = new Server(9999);
    server.setHandler(new FunwebJettyHandler(router));
    server.start();
  }
}