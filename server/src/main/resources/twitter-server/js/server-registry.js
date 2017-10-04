google.load('visualization', '1', { 'packages': ['corechart'], callback: graphLibLoaded })

function graphLibLoaded() {
  var interval = {}

  function refreshStats(dds, chartRenderer) {
    clearInterval(interval)
    var url = $("#server-tabs").data("refresh-uri") + "?"
    for (var i = 0; i < dds.length; i++) {
      var key = $(dds[i]).data("key")
      url += "m="+key+"&"
    }

    function render(data) {
      var json = $.parseJSON(data)
      var failures = 0
      var requests = 0
      for (var i = 0; i < json.length; i++) {
        var name = json[i].name
        var delta = json[i].delta
        var id = name.replace(/\//g, "-")
        if (name.indexOf("failures") > -1) failures = delta
        else if (name.indexOf("requests") > -1) requests = delta
        $("#"+id).text(delta)
      }

      var sr = 0.0
      if (requests > 0) sr = Number(((1.0 - failures/requests)*100.0).toFixed(4))
      chartRenderer.appendMetric({ delta: sr })
    }

    interval = setInterval(function() {
      $.ajax({
        url: url,
        dataType: "text",
        success: render
      })
    }, 1000)
  }

  $('a[data-toggle="tab"]').on('shown.bs.tab', function(e) {
    var active = $("#servers").find(".tab-pane.active")
    var graphDiv = active.find("#server-graph")
    var chart = new ChartRenderer(graphDiv[0], "Success Rate")
    refreshStats(active.find("dd"), chart)
  })

  $('#server-tabs a:first').tab('show')
}