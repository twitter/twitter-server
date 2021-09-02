package com.twitter.server.view

import com.twitter.finagle.{Http, Name, Service}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.loadbalancer.{BalancerRegistry, Balancers}
import com.twitter.server.util.JsonUtils
import com.twitter.util.{Await, Future}
import org.scalatest.funsuite.AnyFunSuite

class BalancersJsonViewTest extends AnyFunSuite {

  test("renders balancer json view") {
    val label = "balancerjsonview"
    val svc: Service[Request, Response] = Service.const(Future.value(Response()))
    val client =
      Http.client
        .withLoadBalancer(Balancers.p2cPeakEwma())
        .newClient(Name.bound(svc), label)

    val registry = BalancerRegistry.get.allMetadata.filter(_.label == label)
    assert(registry.size == 1)

    val json = (new BalancersJsonView(registry)).render
    val expected =
      s"""{
    |  "clients" : [
    |    {
    |      "label" : "$label",
    |      "info" : {
    |        "balancer_class" : "P2CPeakEwma",
    |        "status" : "Open",
    |        "number_available" : 1,
    |        "number_busy" : 0,
    |        "number_closed" : 0,
    |        "total_pending" : 0,
    |        "total_load" : 0.0,
    |        "size" : 1,
    |        "additional_info" : { }
    |      }
    |    }
    |  ]
    |}""".stripMargin

    JsonUtils.assertJsonResponse(json, expected)
    Await.result(client.close())
  }
}
