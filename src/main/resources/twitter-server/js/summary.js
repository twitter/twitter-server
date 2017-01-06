var waitForDom = setInterval(function() {
  if ($("#process-info") != null) {
    clearInterval(waitForDom)
    loadProcInfo()
    loadClientInfo()
    loadServerInfo()
    loadLintInfo()
  }
}, 250)

function loadProcInfo() {
  var url = $("#process-info").data("refresh-uri") + "?"
  var list = $("#process-info ul li")

  for (var i = 0; i < list.length; i++) {
    var key = $(list[i]).data("key")
    if (key != undefined)
      url += "&m="+key
  }

  var msToStr = new MsToStringConverter()
  var bytesToStr = new BytesToStringConverter()

  function pretty(name, value) {
    if (name === "jvm/uptime") return msToStr.convert(value)
    else if (name === "jvm/mem/current/used") return bytesToStr.convert(value)
    else if (name === "jvm/gc/msec") return msToStr.convert(value)
    else return value
  }

  function renderProcInfo(data) {
    var json = $.parseJSON(data)
    for (var i = 0; i < json.length; i++) {
      var id = json[i].name.replace(/\//g, "-")
      var value = pretty(json[i].name, json[i].value)
      $("#"+id).text(value)
    }
  }

  function fetchProcInfo() {
    $.ajax({
      url: url,
      dataType: "text",
      cache: false,
      success: renderProcInfo
    })
  }

  fetchProcInfo()
  setInterval(fetchProcInfo, 1000)
}

function loadClientInfo() {

  function fetchClientInfo() {
    $.ajax({
      url: $("#client-info").data("refresh-uri"),
      dataType: "text",
      cache: false,
      success: function(data) { $("#client-info").html(data) }
    })
  }

  fetchClientInfo()
  setInterval(fetchClientInfo, 1000)
}

function loadServerInfo() {
  $.ajax({
    url: $("#server-info").data("refresh-uri"),
    dataType: "text",
    cache: false,
    success: function(data) { $("#server-info").html(data) }
  })
}

function loadLintInfo() {
  $.ajax({
    url: $("#lint-warnings").data("refresh-uri") + "?",
    dataType: "text",
    cache: false,
    success: function(data) { $("#lint-warnings").html(data) }
  })
}
