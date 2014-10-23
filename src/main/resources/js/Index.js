/*
Note: JQuery selectors are not used for all elements
because of issues with special characters ('/'). In the future,
JQuery selectors should be used.
*/

google.load('visualization', '1', {'packages':['corechart']})
google.setOnLoadCallback(loaded)

function loaded() {
  var numLeastPerformantClients = 4
  var clientElements = []

  var name = {
    client: "client",
    clientName: "client-name",
    clientPorts: "client-ports",
    successRate: "success-rate",
    successRateText: "success-rate-text",
    metricsHighlights: "metrics-highlights",
    clients: "least-performant-clients",
    chart: "chart-div",
    seeAllFlags: "see-all-flags",
    hideAllFlags: "hide-all-flags",
    allFlags: "all-flags",
    successRate: "success-rate",
    successRateBad: "success-rate-bad",
    successRatePoor: "success-rate-poor",
    successRateGood: "success-rate-good"
  }

  var elem = {
    clients: document.getElementById(name.clients),
    chart: document.getElementById(name.chart),
    seeAllFlags: document.getElementById(name.seeAllFlags),
    hideAllFlags: document.getElementById(name.hideAllFlags),
    allFlags: document.getElementById(name.allFlags)
  }

  var metricNames = {
    "Success Rate": "successRate",
    "Requests": "requests",
    "Uptime": "jvm/uptime",
    "Thread Count": "jvm/thread/count",
    "Memory": "jvm/mem/current/used",
    "Garbage Collection": "jvm/gc/msec"
  }

  var chartRenderer = new ChartRenderer(elem.chart, "Requests")

  for(var i=0; i< numLeastPerformantClients; i++){
    var clientOuterElement = createAndAppendElement(type.div, columns.three, elem.clients)
    var clientElement = createAndAppendElement(type.div, name.client, clientOuterElement)
    var clientNameElement = createAndAppendElement(type.a, name.clientName, clientElement)
    var successRateElement = createAndAppendElement(type.a, name.successRate, clientElement)
    var successRateTextElement = createAndAppendElement(type.div, name.successRateText, clientElement)

    var metricsHighlightsElement = createAndAppendElement(type.table, name.metricsHighlights, clientElement)
    var clientPortsElement = createAndAppendElement(type.div, name.clientPorts, clientElement)

    var clientElement = {
      name: clientNameElement,
      successRate: successRateElement,
      successRateTextElement: successRateTextElement,
      ports: clientPortsElement
    }

    clientElements.push(clientElement)
  }

  $(elem.seeAllFlags).click({allFlagsElem: elem.allFlags, seeAllFlagsElem: elem.seeAllFlags, hideAllFlagsElem: elem.hideAllFlags}, function(event){
    hideElement(event.data.seeAllFlagsElem)
    showElement(event.data.hideAllFlagsElem)
    showElement(event.data.allFlagsElem)
  })

  $(elem.hideAllFlags).click({allFlagsElem: elem.allFlags, seeAllFlagsElem: elem.seeAllFlags, hideAllFlagsElem: elem.hideAllFlags}, function(event){
    showElement(event.data.seeAllFlagsElem)
    hideElement(event.data.hideAllFlagsElem)
    hideElement(event.data.allFlagsElem)
  })

  function addValuesToQueryList(obj) {
    var query = ""
    for(var key in obj) {
      query += "serverMetric=" + obj[key] + "&"
    }
    return query
  }

  var metricsQueryUrl = "/admin/metrics?" + addValuesToQueryList(metricNames) +
    "&clientMetric=successRate&sortClientsBy=successRate&sortClientsOrder=asc"

  function preLoadSummary(){
    $.ajax({
      url: metricsQueryUrl,
      success:doLoadSummary,
    })
  }

  function doLoadSummary(){
    window.setInterval(loadSummary, 1000)
  }

  function loadSummary(){
    $.ajax({
      url: metricsQueryUrl,
      success:renderSummary,
    })
  }

  function renderMetrics(metrics, element) {
    $(element).empty()
    var tbody = document.createElement(type.tbody)
    for(var i=0; i< metrics.length; i++) {
      var tr = document.createElement(type.tr)

      var td = document.createElement(type.td)
      var link = document.createElement(type.a)
      link.title = metrics[i]["name"]
      link.innerHTML = metrics[i]["name"]
      link.href = "/admin/metrics_graphs#" + metricNames[metrics[i]["name"]]
      td.appendChild(link)
      tr.appendChild(td)

      var td = document.createElement(type.td)
      var link = document.createElement(type.a)
      link.title = metrics[i]["value"]
      link.innerHTML = metrics[i]["value"]
      link.href = "/admin/metrics_graphs#" + metricNames[metrics[i]["name"]]
      td.appendChild(link)
      tr.appendChild(td)

      tbody.appendChild(tr)
    }
    element.appendChild(tbody)
  }

  function popMetric(name, metrics) {
    for(var i=0; i< metrics.length; i++){
      if(metrics[i].name.search(name) != -1){
        var metric = metrics[i]
        metrics.splice(i, 1)
        return metric
      }
    }
  }

  function getMetric(name, metrics) {
    for(var i=0; i< metrics.length; i++){
      if(metrics[i].name === name)
        return metrics[i]
    }
  }

  function replaceMetric(name, newValue, metrics) {
    for(var i=0; i< metrics.length; i++){
      if(metrics[i].name === name)
        metrics[i].value = newValue
    }
  }

  function replaceMatchingValuesWithKeys(objArray, repl) {
    for(var i=0; i< objArray.length; i++){
      var obj = objArray[i]
      for(var objKey in obj) {
        for(var replKey in repl){
          if(obj[objKey] == repl[replKey]){
            obj[objKey] = replKey
          }
        }
      }
    }
  }

  function renderSuccessRate(successRate, element, textElement) {
    element.innerHTML =  (successRate * 100).toFixed(1) + "%"
    textElement.innerHTML = "Success Rate"
    element.className = name.successRate
    if(successRate < 0.9) $(element).addClass(name.successRateBad)
    else if(successRate < 0.99) $(element).addClass(name.successRatePoor)
    else $(element).addClass(name.successRateGood)
  }

  function renderList(list, element) {
    $(element).empty()
    var ul = document.createElement('ul')
    for(var i=0; i<list.length; i++) {
      var li = document.createElement('li')
      li.innerHTML = list[i]
      ul.appendChild(li)
    }
    element.appendChild(ul)
  }

  function renderClient(client, element) {
    element.name.innerHTML = client.name
    element.name.href = "/admin/clients/" + client.name
    element.successRate.href = "/admin/metrics_graphs#clnt/" + client.name + "/"
    var successRate = popMetric("clnt/" + client.name + "/successRate", client.metrics).value
    renderSuccessRate(successRate, element.successRate, element.successRateTextElement)
    renderList(client.ports, element.ports)
  }

  var msToStringConverter = new MsToStringConverter()
  var bytesToStringConverter = new BytesToStringConverter()

  function renderSummary(data) {
    var jsonData = jQuery.parseJSON(data)
    var serverMetrics = jsonData.server

    var successRateElem = document.getElementById('success-rate-total')
    var successRateTextElem = document.getElementById('success-rate-total-text')

    if(serverMetrics.length == 0){
      successRateTextElem.innerHTML = "No metrics information returned. This feature is only " +
      "available when depending on finagle-stats."
    }

    var successRateMetric = popMetric(metricNames["Success Rate"], serverMetrics)
    successRateElem.href = "/admin/metrics_graphs#" + stripGroup(successRateMetric.name, 2)
    var successRate = successRateMetric.value
    var requestsMetric = popMetric(metricNames["Requests"], serverMetrics)
    chartRenderer.appendMetric(requestsMetric)

    var uptime = getMetric(metricNames["Uptime"], serverMetrics).value
    replaceMetric(metricNames["Uptime"], msToStringConverter.convert(uptime), serverMetrics)

    var memory = getMetric(metricNames["Memory"], serverMetrics).value
    replaceMetric(metricNames["Memory"], bytesToStringConverter.convert(memory), serverMetrics)

    var gc = getMetric(metricNames["Garbage Collection"], serverMetrics).value
    replaceMetric(metricNames["Garbage Collection"], bytesToStringConverter.convert(gc) + "/ms", serverMetrics)

    var metricsHighlights = document.getElementById('metrics-highlights-total')
    replaceMatchingValuesWithKeys(serverMetrics, metricNames)
    renderMetrics(serverMetrics, metricsHighlights)
    renderSuccessRate(successRate, successRateElem, successRateTextElem)

    var clients = jsonData.clients
    for(var i=0; i< clients.length; i++){
      if(clients[i].metrics.length == 0){
        clients.splice(i, 1)
        i--
      }
    }

    for(var i=0; i< numLeastPerformantClients; i++){
      var clientLength = clients.length
      if(i < clients.length){
        var client = clients[i]
        renderClient(client, clientElements[i])
      }
    }
  }
  preLoadSummary()
}
