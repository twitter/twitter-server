package com.twitter.server.view
import com.twitter.finagle.client.{ClientInfo, ClientModuleInfo}
 
private[server] trait View
  
private[server] object ViewUtils {
  def mapParams(params:  Map[String, String]) =
    params.toList map { 
      case (k, v) => Map("key" -> k, "value" -> pretty(v))
    }

  // Strip extraneous symbols from toString'd objects
  private[this] def pretty(value: String): String = 
    """.+\.([\w]+)[$|@]""".r.findFirstMatchIn(value) match {
      case Some(name) => name.group(1)
      case _ => """\((.+)\)""".r.findFirstMatchIn(value) match {
        case Some(name) => name.group(1)
        case _ => value
      }
  }
}

private[server] class ClientInfoView(client: ClientInfo) extends View { 
  val name = client.name  
  val modules = client.modules.toList map {
    case ClientModuleInfo(role, description, perModuleParams) => 
      Map("role" -> role, "description" -> description, "perModuleParams" -> 
        ViewUtils.mapParams(perModuleParams))
  }
}

private[server] class ClientListView(val clients: List[String], val baseUrl: String) extends View