package com.twitter.server.view

import com.twitter.finagle.util.StackRegistry

private[server] object StackRegistryView {
  /**
   * Render a stack entry, along with its params, in an html template.
   * @param entry The stack registry entry.
   * @param statScope The finagle StatsReceiver scope with which the
   * entries stats are exported. If present, the html page will link
   * to the metrics graphs for this entry.
   */
  def render(
    entry: StackRegistry.Entry,
    statScope: Option[String]
  ): String = {

    def renderParams(params: Seq[(String, String)]): String =
      (for ((field, value) <- params) yield {
        s"""<tr>
              <td>${field}</td>
              <td>${value}</td>
            </tr>"""
      }).mkString("\n")

    val modules = entry.modules

    s"""<h2>
          ${entry.name}
          <small>${entry.addr}</small>
        </h2>
        ${
           (for (scope <- statScope) yield {
            s"""<a href="/admin/metrics#${scope}/requests"
                class="btn btn-default">
              <span class="glyphicon glyphicon-stats"></span>
                Watch metrics for ${entry.name}
              </a>"""
           }).getOrElse("")
         }
        <br/><br/>
        <div class="row">
          <div class="col-md-12">
            <!-- tab nav -->
            <div class="tabbable tabs-left">
              <ul class="nav nav-tabs">
                ${
                   (for (StackRegistry.Module(role, _, _) <- modules) yield {
                      s"""<li><a href="#${role}-module" data-toggle="tab">${role}</a></li>"""
                    }).mkString("\n")
                 }
              </ul>
            </div>
            <!-- tab content -->
            <div class="tab-content">
              ${
                (for (StackRegistry.Module(role, desc, params) <- modules) yield {
                  s"""<div class="tab-pane" id="${role}-module">
                        <div style="display:inline-block; min-width:50%">
                          <div class="panel panel-default">
                            <div class="panel-heading">${role}</div>
                            <div class="panel-body"><p>${desc}</p></div>
                            ${
                              if (params.isEmpty) "" else {
                                 s"""<table class="table table-condensed table-bordered">
                                  <thead>
                                    <tr>
                                      <th>Parameter</th>
                                      <th>Value</th>
                                    </tr>
                                  </thead>
                                  <tbody>${renderParams(params)}</tbody>
                                </table>"""
                              }
                            }
                          </div>
                        </div>
                      </div>"""
                }).mkString("\n")
              }
            </div>
        </div>"""
  }
}
