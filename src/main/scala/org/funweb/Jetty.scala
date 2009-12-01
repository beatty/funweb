package org.funweb

// Jetty-specific parts of MockRequest

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.mortbay.jetty.handler.AbstractHandler
import org.eclipse.jetty.continuation.{Continuation,ContinuationSupport}
import scala.actors.Actor
import scala.actors.Actor._

/**
 * The MockRequest handler for Jetty
 */
class FunwebJetty6Handler(router: Router) extends AbstractHandler {
  def handle(s: String, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse, dispatch: Int) {
    val request = new ServletBackedRequest(servletRequest)
    router.find(request) match {
      case Some(x) => {
        request.postDispatch(x._1._1, x._2)
        val response = x._1._2(request)
        servletResponse.setStatus(response.status.code, response.status.message.getOrElse(null))
        response.headerOps.foreach(op => op.execute(servletResponse))
        servletRequest.asInstanceOf[org.mortbay.jetty.Request].setHandled(true);
        response.body.foreach(body => {
          body.mediaType.foreach(mediaType => servletResponse.setContentType(mediaType.toHttpString))
          body.contentLength.foreach(len => servletResponse.setContentLength(len))
          body.write(servletResponse.getOutputStream)
          servletResponse.flushBuffer
        })
      }
      case None => {}
    }
  }
}


class FunwebContinuation(val continuation: Continuation, val handler: (Request)=>Response, val routeInfo: RouteInfo)

class ExampleThread(val continuation: Continuation, val handler: (Request)=>Response, val routeInfo: RouteInfo) extends Thread {
  override def run() {
    Thread.sleep(500)    
    continuation.setAttribute("_continue", new FunwebContinuation(continuation, handler, routeInfo))
    println("calling resume")
    continuation.resume()
  }
}


class MinThread(val continuation: Continuation) extends Thread {
  override def run() {
    Thread.sleep(500)    
    continuation.setAttribute("results", "42")
    continuation.resume()
  }
}

class MinAsyncHandler extends org.eclipse.jetty.server.handler.AbstractHandler {
  def handle(target: String, baseRequest: org.eclipse.jetty.server.Request, request: HttpServletRequest, response: HttpServletResponse) {
    println("woot")
     val results = request.getAttribute("results");
     if (results == null)
     {
       println("here")
       val continuation = ContinuationSupport.getContinuation(request);
       continuation.suspend();
       new MinThread(continuation).start()
     } else {
       println("there")
       response.setStatus(200)
       response.getOutputStream().write(results.asInstanceOf[String].getBytes)
       response.flushBuffer
     }    
  }
}

object HandlerActor extends Actor {
  def act() {
    loop {
      react {
        case (request: HttpServletRequest, handler: Function1[Request,Response], continuation: Continuation) => {
          println("act()!")
          //handler.apply(request)
          continuation.setAttribute("_continue", new FunwebContinuation(continuation, handler, null)) // TODO: routeInfo
          continuation.resume()        
        }
      }
    }
  }
}

class FunwebJetty7Handler(router: Router) extends org.eclipse.jetty.server.handler.AbstractHandler {
  def handle(s: String, baseRequest: org.eclipse.jetty.server.Request, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) {
    // TODO: remove
    if (s == "/favicon.ico") {
      servletResponse.setStatus(404)
      return
    }
    
    println("request received: " + s)
    val request = new ServletBackedRequest(servletRequest)
    
    // check for stored continue function
    val continueAttr = servletRequest.getAttribute("_continue")
    if (continueAttr != null) {
      println("CONTINUE! " + continueAttr)
    }
    
    var continue = if (continueAttr != null) continueAttr.asInstanceOf[FunwebContinuation] else null
    
    var handleFunc = if (continue != null) continue.handler else null
    var routeInfo = if (continue != null) continue.routeInfo else null
    //request.postDispatch(x._1._1, x._2)
    
    if (handleFunc == null) {
      println("no stored handle func")
      val routeResponse = router.find(request)
      if (routeResponse != None) {
        handleFunc = routeResponse.get._1._2
        routeInfo = routeResponse.get._2
      }
      
      println("found handle func: " + handleFunc)
    } else {
      println("there was a stored handle func: " + handleFunc)
    }

    println("executing handle func")
    request.postDispatch(null, routeInfo) // TODO: lug around route?
    val response = handleFunc(request)

    if (response.isInstanceOf[ContinueResponse]) {
      println("received continuation response")
      val continuation = ContinuationSupport.getContinuation(servletRequest);
      continuation.suspend(servletResponse)
      HandlerActor ! (servletRequest, response.asInstanceOf[ContinueResponse].handler, continuation)
      //new ExampleThread(continuation, response.asInstanceOf[ContinueResponse].handler, routeInfo).start()      
    } else {
      println("sending response")
      servletResponse.setStatus(response.status.code, response.status.message.getOrElse(null))
      response.headerOps.foreach(op => op.execute(servletResponse))
      baseRequest.setHandled(true);
      response.body.foreach(body => {
        body.mediaType.foreach(mediaType => servletResponse.setContentType(mediaType.toHttpString))
        body.contentLength.foreach(len => servletResponse.setContentLength(len))
        body.write(servletResponse.getOutputStream)
        servletResponse.flushBuffer
      })      
    }    
  }
}