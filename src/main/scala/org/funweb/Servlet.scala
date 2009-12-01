package org.funweb

import javax.servlet.http.HttpServletRequest

// implements parts of MockRequest in terms of Servlets

/**
 * AMockRequest Request object that largely delegates to an HttpServletRequest
 */
private class ServletBackedRequest(private val request: HttpServletRequest) extends SkinnyRequest with Request {
  private var routeInfoOption: Option[RouteInfo] = None
  private var routeOption: Option[Route] = None  
  private val noPostDispatchException = new IllegalStateException("postDispatch() has not been called")

  def route = routeOption.getOrElse(throw noPostDispatchException)
  def method = request.getMethod

  def routeParam(name: String) = routeInfoOption.getOrElse(throw noPostDispatchException).param(name)

  def queryParam(name: String): Option[String] = {
    if (method != "GET") throw new IllegalStateException("only GET methods have query parameters")
    request.getParameter(name) match {
      case null => None
      case v => Some(v)
    }
  }
  
  def formParam(name: String): Option[String] = {
    if (method != "POST" && method != "PUT") throw new IllegalStateException("only POST and PUT methods have form parameters")
    request.getParameter(name) match {
      case null => None
      case v => Some(v)
    }
  }
  
  def header(name: String): Option[String] = {
    request.getHeader(name) match {
      case null => None
      case v => Some(v)
    }
  }
  
  def path() = request.getRequestURI()

  def fullUrl() = request.getProtocol + "://" + header("Host").getOrElse("") + "/" + request.getRequestURI
  def cookie(name: String): Option[Cookie] = {
    request.getCookies.find(_.getName == name) match {
      case Some(v) => Some(new ServletBackedCookie(v))
      case None => None
    }
  }

  /**
   *
   */  
  def postDispatch(route: Route, routeInfo: RouteInfo) {
    this.routeOption = Some(route)
    this.routeInfoOption = Some(routeInfo)
  }
}

/**
 * A MockRequest Cookie object that delegates to an HttpServletRequest
 */
class ServletBackedCookie(private val cookie: javax.servlet.http.Cookie) extends Cookie {
  def name = cookie.getName
  def path = cookie.getPath
  def comment = cookie.getComment
  def domain = cookie.getDomain
  def maxAge = cookie.getMaxAge
  def secure = cookie.getSecure
  def version = cookie.getVersion
}
