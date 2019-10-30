package com.twitter.server.view

import com.twitter.finagle.{Addr, Dentry, Dtab}
import com.twitter.finagle.Namer.AddrWeightKey

private[server] object EndpointRegistryView {

  private[this] def renderDtab(dtab: Dtab): String =
    s"""
      <ul class="dtab">
      ${(for (Dentry(prefix, dst) <- dtab) yield {
      s"""<li>${prefix.show} => ${dst.show}</li>"""
    }).mkString("")}
      </ul>"""

  /**
   * Render current weights and socket addresses for paths in each dtab
   * @param dtabEntries Map of Dtabs to map of paths to Addrs
   */
  def render(dtabEntries: Map[Dtab, Map[String, Addr]]): String =
    s"""
    <div class="row">
      <div class="col-md-12">
        <a name="endpoints"></a>
        <h3>Endpoints</h3>
        <ul>
        ${(for ((dtab, observations) <- dtabEntries) yield {
      s"""
            <li>Dtab<br/>
              <div>${renderDtab(dtab)}</div>
              <div>Resolved Endpoints<br/>
                <ul>
                ${(for ((path, addr) <- observations) yield {
        s"""<li>Path: ${path}<br/>""" +
          (addr match {
            case Addr.Bound(endpoints, metadata) =>
              val weight: Double = metadata.get(AddrWeightKey) match {
                case Some(w: Double) => w
                case Some(failed) =>
                  // Namer records weights as Doubles, so this should never happen.
                  -1.0
                case None => 1.0
              }
              s"""Weight: ${weight}<br/>
                            Endpoints:<br/>
                              <ul>
                              ${(for (endpoint <- endpoints) yield {
                s"""<li>${endpoint}</li>"""
              }).mkString("\n")}
                              </ul>
                        </li>"""
            case Addr.Failed(why) =>
              s"""Endpoint Resolution Failed: ${why}</li>"""
            case _ =>
              s"""Unbound Addr: ${addr}</li>"""
          })
      }).mkString("\n")}
                </ul>
              </div>
            </li>"""
    }).mkString("\n")}
        </ul>
      </div>
    </div>"""
}
