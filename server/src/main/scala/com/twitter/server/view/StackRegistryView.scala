package com.twitter.server.view

import com.twitter.finagle.util.StackRegistry
import com.twitter.server.util.HtmlUtils.escapeHtml

private[server] object StackRegistryView {

  /**
   * Render a stack entry, along with its params, in an html template.
   * @param entry The stack registry entry.
   * @param statScope The finagle StatsReceiver scope with which the
   * entries stats are exported. If present, the html page will link
   * to the metrics graphs for this entry.
   */
  def render(entry: StackRegistry.Entry, statScope: Option[String]): String = {

    def renderParams(params: Seq[(String, String)]): String =
      (for ((field, value) <- params) yield {
        s"""<tr>
              <td>${escapeHtml(field)}</td>
              <td class="stack-value">${escapeHtml(value)}</td>
            </tr>"""
      }).mkString("\n")

    // Including turn an item to an id attribute, use underscores instead of plus signs
    def toUri(item: String): String =
      item.replace(' ', '_')

    val modules = entry.modules

    s"""<h2>
          ${escapeHtml(entry.name)}
          <small>${escapeHtml(entry.addr)}</small>
        </h2>
        ${(for (scope <- statScope) yield {
      s"""<a href="/admin/metrics#${scope}/requests"
                class="btn btn-default">
              <span class="glyphicon glyphicon-stats"></span>
                Watch metrics for ${escapeHtml(entry.name)}
              </a>"""
    }).getOrElse("")}
        <br/><br/>
        <div class="row stack-registry">
          <div class="col-md-12">
            <a name="stack"></a>
            <div class="row">
              <!-- tab nav -->
              <div class="tabtable tabs-left col-xs-6 col-sm-6 col-md-4 col-lg-3">
                <ul class="nav nav-tabs">
                  <li>Stack Modules</li>
                  ${(for (StackRegistry.Module(role, _, _) <- modules) yield {
      s"""<li><a href="#${toUri(role)}-module" data-toggle="tab">${escapeHtml(role)}</a></li>"""
    }).mkString("\n")}
                </ul>
              </div>
              <!-- tab content -->
              <div class="tab-content col-xs-6 col-sm-6 col-md-8 col-lg-9">
                ${(for (StackRegistry.Module(role, desc, params) <- modules) yield {
      s"""<div class="tab-pane" id="${toUri(role)}-module">
                          <div class="stack-module-panel">
                            <div class="panel panel-default">
                              <div class="panel-heading">${escapeHtml(role)}</div>
                              <div class="panel-body"><p>${escapeHtml(desc)}</p></div>
                              ${if (params.isEmpty) ""
      else {
        s"""<table class="table table-condensed table-bordered">
                                    <thead>
                                      <tr>
                                        <th>Parameter</th>
                                        <th>Value</th>
                                      </tr>
                                    </thead>
                                    <tbody>${renderParams(params)}</tbody>
                                  </table>"""
      }}
                            </div>
                          </div>
                        </div>"""
    }).mkString("\n")}
              </div>
            </div>
          </div>
        </div>"""
  }
}
