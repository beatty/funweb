package org.funweb

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import scala.collection.mutable.Stack

case class MockRequest(val method: String, val path: String) extends SkinnyRequest {
  def queryParameter(name: String) = None
  def header(name: String) = None  
}

class DispatcherSpec extends FlatSpec with ShouldMatchers {
  val route = RegexRoute("GET", "/person/([A-Za-z0-9]+)/profile", "username" :: Nil)
  val request = MockRequest("GET", "/person/john/profile")

  "A regex matcher" should "have true positives" in {
    route.matches(request) should not be (None)
  }
  
  "A regex matcher" should "not have false positives" in {
    route.matches(MockRequest("GET", "/")) should be (None)
    route.matches(MockRequest("GET", "/person")) should be (None)
    route.matches(MockRequest("GET", "/person/john")) should be (None)
    route.matches(MockRequest("GET", "/person/john/")) should be (None)
    route.matches(MockRequest("GET", "/person/john/profile/")) should be (None)
  }
    
  "A regex matcher" should "extract path parameters" in {
    route.matches(request).get.param("username") should equal ("john")
  }
    
  "A regex matcher" should "fail when there are more param names than capture groups" in {
    val badroute = RegexRoute("GET", "/person/[A-Za-z0-9]+/profile", "username" :: Nil)    
    evaluating { badroute.matches(request).get.param("username") } should produce [IllegalArgumentException]
  }
  
  "A regex matcher" should "fail when there are more capture groups than param names" in {
    val badroute = new RegexRoute("GET", "/person/([A-Za-z0-9]+)/profile", Nil)    
    evaluating { badroute.matches(request).get.param("username") } should produce [IllegalArgumentException]
  }

  "A regex matcher" should "fail when constructed with malforumed regex" in {
    evaluating { new RegexRoute("GET", "/person/[A-]+)/profile", Nil) } should produce [java.util.regex.PatternSyntaxException]
  }

  "a dispatcher" should "dispatcher to first matching route" in {
    val handler = (request: Request) => NotFound
    val router = new Router((route, handler) :: Nil)
    val response = router.find(request).get
    response._1._1 should be (route)
    response._1._2 should be (handler)
    response._2.param("username") should be ("john")
  }

  "a media type" should "generate http headers" in {
    new MediaType("text/html", List(("charset","UTF-8"))).toHttpString() should be ("text/html; charset=UTF-8")
  }
}