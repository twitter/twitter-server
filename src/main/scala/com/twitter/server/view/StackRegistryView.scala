package com.twitter.server.view

import com.twitter.finagle.util.StackRegistry

private[server] object StackRegistryView {

  /** Strip extraneous symbols from toString'd objects. */
  private def pretty(value: String): String =
    """.+\.([\w]+)[$|@]""".r.findFirstMatchIn(value) match {
      case Some(name) => name.group(1)
      case _ => """\((.+)\)""".r.findFirstMatchIn(value) match {
        case Some(name) => name.group(1)
        case _ => value
      }
    }

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
              <td>${pretty(value)}</td>
            </tr>"""
      }).mkString("\n")

    // Introspect the entries stack and params. We limit the
    // reflection of params to case classes.
    val modules = (entry.stack.tails map { node =>
      val raw = node.head.parameters.map { p => entry.params(p) }
      val reflected = raw.foldLeft(Seq[(String, String)]()) {
        case (seq, p: Product) =>
          val fields = p.getClass.getDeclaredFields.map(_.getName).toSeq
          val values = p.productIterator.map(_.toString).toSeq
          seq ++ (fields.zipAll(values, "<unknown>", "<unknown>"))

        case (seq, _) => seq
      }
      (node.head.role.name, node.head.description, reflected)
    }).toSeq

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
                   (for ((role, _, _) <- modules) yield {
                      s"""<li><a href="#${role}-module" data-toggle="tab">${role}</a></li>"""
                    }).mkString("\n")
                 }
              </ul>
            </div>
            <!-- tab content -->
            <div class="tab-content">
              ${
                (for ((role, desc, params) <- modules) yield {
                  s"""<div class="tab-pane" id="${role}-module">
                        <div style="display:inline-block; width:50%">
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