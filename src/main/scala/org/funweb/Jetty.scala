package org.funweb

// Jetty-specific parts of MockRequest

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.mortbay.jetty.handler.AbstractHandler

/**
 * The MockRequest handler for Jetty
 */
class FunwebJettyHandler(router: Router) extends AbstractHandler {
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