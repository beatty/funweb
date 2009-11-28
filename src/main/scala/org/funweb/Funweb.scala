package org.funweb

import javax.servlet.http.{HttpServletResponse}
import java.util.regex.{Matcher, Pattern}
import org.mortbay.util.IO
import java.io.{FileInputStream, File, InputStream, OutputStream}

/**
 * A skinny request interface used in some contexts where touching the body is bad, such as request dispatching
 */
trait SkinnyRequest {
  def method: String
  def path: String
  def queryParameter(name: String): Option[String]
  def header(name: String): Option[String]  
}

/**
 * An HTTP request
 * TODO: add request entity support, probably uses Commons FileUpload
 */
trait Request {
  def route(): Route
  def routeInfo(): RouteInfo
  def routeParam(name: String): String = routeInfo.param(name)
  def header(name: String): Option[String]
  def cookie(name: String): Option[Cookie]
  def queryParameter(name: String): Option[String]
  def formParameter(name: String): Option[String]
  def method: String
  def fullUrl(): String
  def path: String  
}

/**
 * An HTTP response, consisting of a status, a list of header operations (such as 'add header', 'remove cookie', etc.), and a body
 */
trait Response {
  def status(): Status
  def headerOps(): List[HeaderOp]
  def body(): Option[Body]
}

/**
 * A response body
 * TODO: add caching directive support
 */
trait Body {
  def write(out: OutputStream)
  def contentLength(): Option[Int] = None
  def mediaType(): Option[MediaType] = None
}

trait Route {
  def matches(request: SkinnyRequest): Option[RouteInfo]
}

trait RouteInfo {
  def param(name: String): String
}

/**
 * An HTTP response, composed of a status and a list of instructions
 */
class SimpleResponse(val status: Status, val headerOps: List[HeaderOp], val body: Option[Body]) extends Response

class RedirectResponse(code: Int, location: String) extends SimpleResponse(Status(code, None), List(new AddLocationHeader(location)), None)
class PermanentRedirect(location: String) extends RedirectResponse(301, location)
class TemporaryRedirect(location: String) extends RedirectResponse(307, location)
class SeeOther(location: String) extends RedirectResponse(303, location)
class NotFound(body: Option[Body]) extends SimpleResponse(NotFoundStatus, Nil, body)
object NotFound extends NotFound(None)

/**
 * An HTTP status, composed of a status code and a status message
 */
case class Status(val code: Int, val message: Option[String])
object Ok extends Status(200, None)
object NotFoundStatus extends Status(404, None)

class MediaType(val contentType: String, val parameters: List[Tuple2[String,String]]) {
  def this(contentType: String) = this(contentType, Nil)
  def toHttpString() = {
    if (parameters.size > 0) {
      contentType + parameters.map(x => {"; " + x._1 + "=" + x._2}).reduceLeft(_+_)
    } else {
      // required because Nil doesn't implement reduceLeft
      contentType
    }
  }
}

object TextHtml extends MediaType("text/html", List(("charset","UTF-8")))
object TextPlain extends MediaType("text/plain", Nil)
class HtmlBody(string: String) extends StringBody(string, Some(TextHtml))
class TextBody(string: String) extends StringBody(string, Some(TextPlain))
class HtmlResponse(html: String) extends SimpleResponse(Ok, Nil, Some(new HtmlBody(html)))
class TextResponse(body: String) extends SimpleResponse(Ok, Nil, Some(new TextBody(body)))
class FileResponse(file: File) extends SimpleResponse(Ok, Nil, Some(new FileBody(file)))

class StringBody(string: String, override val mediaType: Option[MediaType]) extends Body {
  val bytes = string.getBytes()

  def write(out: OutputStream) {
    out.write(bytes)
  }

  override def contentLength(): Option[Int] = if (bytes.length <= Integer.MAX_VALUE) Some(bytes.length.asInstanceOf[Int]) else None
}


class InputStreamBody(in: InputStream) extends Body {
  def write(out: OutputStream) {
    IO.copy(in, out)
  }
}

class FileBody(f: File) extends InputStreamBody(new FileInputStream(f)) {
  override def contentLength(): Option[Int] = {
    val len = f.length
    if (len <= Integer.MAX_VALUE) Some(len.asInstanceOf[Int]) else None
  }
}

/**
 * An HTTP cookie
 */
trait Cookie {
  def name: String
  def path: String
  //def expires: Long
  def comment: String
  def domain: String
  def maxAge: Int
  def secure: Boolean
  def version: Int
  //val httponly: Boolean // ?? see http://docs.python.org/library/cookie.html
}

trait HeaderOp {
  def execute(response: HttpServletResponse): Unit
}

class AddCookie(val name: String, val value: String) extends HeaderOp {
  def execute(response: HttpServletResponse) {
    response.addCookie(new javax.servlet.http.Cookie(name, value))
  }
}

class ClearCookie(val name: String, val path: String, val domain: String) extends HeaderOp {
  def execute(response: HttpServletResponse) {
    val c = new javax.servlet.http.Cookie(name, "")
    if (path != null) c.setPath(path)
    if (domain != null) c.setDomain(domain)
    response.addCookie(c)
  }
}

class AddHeader(val name: String, val value: String) extends HeaderOp {
  def execute(response: HttpServletResponse) {
    response.addHeader(name, value)
  }
}

class AddLocationHeader(location: String) extends HeaderOp {
  def execute(response: HttpServletResponse) {
    response.addHeader("Location", location)
  }
}

/**
 * A route specified as a regular expression. The regular expression can have capture groups for
 * the URL "path parameters". As capture groups cannot be named in Java regular expressions, a
 * List of names must be provided. The size of the list must equal of the number of capture groups
 * in the expression.
 */
case class RegexRoute(method: String, urlPattern: String, paramNames: List[String]) extends Route {  
  val pattern = Pattern.compile(urlPattern)
  
  def matches(request: SkinnyRequest): Option[RouteInfo] = {
    if (request.method == method) {
      val matcher = pattern.matcher(request.path)
      if (matcher.matches()) Some(new RegexRouteInfo(matcher, paramNames)) else None      
    } else {
      None
    }
  }
}

case class FixedRoute(method: String, urlPattern: String) extends Route {
  def matches(request: SkinnyRequest): Option[RouteInfo] =
    if (request.method == method && request.path == urlPattern) Some(FixedRouteInfo) else None
}

object FixedRouteInfo extends RouteInfo {
  def param(name: String): String = throw new UnsupportedOperationException
}

/**
 * A RouteInfo generated by a RegexRoute. We hold the matcher that was used to verify the match
 * so that we can capture the groups. We also lug along the paramNames list. We lazily evaluate
 * param names as this is probably cheaper/faster than building a map of names->values up front.
 */
class RegexRouteInfo(val matcher: Matcher, val paramNames: List[String]) extends RouteInfo {
  def param(name: String): String = {
    val idx = paramNames.findIndexOf(_ == name)
    if (idx > -1 && idx < matcher.groupCount) matcher.group(idx + 1) else throw new IllegalArgumentException("bad paramName: " + name)
  }
}

class Router(val routes: List[(Route,(Request)=>Response)]) {
  def find(request: SkinnyRequest): Option[((Route,(Request)=>Response),RouteInfo)] = {
    // TODO: is there a list comprehension that will short-circuit at the first match found? not a fan of the
    // fall-through
    for (entry <- routes) {
      entry._1.matches(request) match {
        case Some(info) => return Some((entry,info))
        case None => {}
      }      
    }
    None
  }
}