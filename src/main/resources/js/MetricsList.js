google.load('visualization', '1', {'packages':['corechart']});
google.setOnLoadCallback(loaded);

var intervalId = 0
var metricsQueryUrl = "/admin/metrics?metric="
var selectedGroup = null
var selectedItem = null

function createInterval(f, parameter,interval) {
  clearInterval(intervalId)
  intervalId = setInterval(function() {
    f(parameter);
  }, interval);
}

function showGraph(name) {
  unselectElement(selectedItem)
  selectedItem = document.getElementById(name)
  selectElement(selectedItem)

  function loadMetric(name){
    $.ajax({
      url:metricsQueryUrl + name,
      success:renderMetric
    });
  }

  function preLoadMetric(name){
    $.ajax({
      url: metricsQueryUrl + name,
      success:doLoadMetric(name),
    });
  }

  function doLoadMetric(){
    createInterval(loadMetric, name, 1000);
  }


  var chartRenderer = new ChartRenderer(document.getElementById('chart-div'), name)

  preLoadMetric(name)

  function renderMetric(data) {
    var jsonData = jQuery.parseJSON(data);
    chartRenderer.appendMetric(jsonData.metrics[0])
  }
}

function loaded() {

  new ChartRenderer(document.getElementById('chart-div'), "Select a metric to begin graphing its values.")

  $.ajax({
    url:metricsQueryUrl + "all&sortMetricsOrder=asc",
    success: listMetrics
  })

  function listMetrics(data) {
    var jsonData = jQuery.parseJSON(data)
    var metrics = jsonData.metrics
    var metricsGroupsElement = document.getElementById("metrics-groups")
    var metricsItemsElement = document.getElementById("metrics-items")
    var metricsGroups = {}

    for(var i=0; i< metrics.length; i++){
      var name = metrics[i].name
      var group = stripGroup(name, 2)
      if(!metricsGroups[group]){
        metricsGroups[group] = []
      }
      metricsGroups[group].push(metrics[i])
    }
    for(group in metricsGroups){
      var metricGroupElement = document.createElement('li')
      metricGroupElement.id = group
      metricGroupElement.innerHTML = group
      metricsGroupsElement.appendChild(metricGroupElement)
      var metricsItemsListElement = document.createElement('ul')
      hideElement(metricsItemsListElement)
      metricsItemsElement.appendChild(metricsItemsListElement)


      var itemGroup = metricsGroups[group]
      for(var i=0; i< itemGroup.length; i++){
        var metricItemElement = document.createElement('li')
        metricItemElement.id = itemGroup[i].name
        metricItemElement.innerHTML = itemGroup[i].name.replace(group, '')
        metricsItemsListElement.appendChild(metricItemElement)
        $(metricItemElement).click({name: itemGroup[i].name}, function(event){
          var docScroll=document.body.scrollTop
          var innerScroll = metricsItemsElement.scrollTop
          window.location.hash=event.data.name
          document.body.scrollTop=docScroll
          metricsItemsElement.scrollTop = innerScroll
          showGraph(event.data.name)
        })
      }
      metricsGroups[group] = metricsItemsListElement

      $(metricGroupElement).click({group: group, itemGroup: metricsGroups[group]}, function(event){
        showElement(event.data.itemGroup)
        unselectElement(selectedGroup)

        if(selectedGroup) hideElement(metricsGroups[selectedGroup.id])

        selectedGroup = document.getElementById(event.data.group)
        selectElement(selectedGroup)

        var docScroll=document.body.scrollTop
        var innerScroll = metricsGroupsElement.scrollTop
        window.location.hash=event.data.group
        document.body.scrollTop=docScroll
        metricsGroupsElement.scrollTop = innerScroll
      })
    }

    var currentMetric = window.location.hash.replace('#', '')
    if(currentMetric) {
      if(metricsGroups[currentMetric]){
        selectedGroup = document.getElementById(currentMetric)
        selectElement(selectedGroup)
        showElement(metricsGroups[currentMetric])
      }else{
        selectedGroup = document.getElementById(stripGroup(currentMetric, 2))
        selectElement(selectedGroup)
        showElement(metricsGroups[selectedGroup.id])
        selectedGroup.scrollIntoView(true)
        showGraph(currentMetric)
      }
      document.getElementById(currentMetric).scrollIntoView(true)
    }
  }
}
