function ChartRenderer(element, title) {
  //remove old chart
  $(element).empty()

  var titleDiv = document.createElement('h4');
  titleDiv.id = "chart-title"
  titleDiv.innerHTML = title
  element.appendChild(titleDiv)

  var chartDiv = document.createElement('div');
  element.appendChild(chartDiv)

  var timePassedDiv = document.createElement('div');
  timePassedDiv.id = "time-passed"
  timePassedDiv.innerHTML = "1 minute ago"
  element.appendChild(timePassedDiv)

  var timeNowDiv = document.createElement('div');
  timeNowDiv.id = "time-now"
  timeNowDiv.innerHTML = "Now"
  element.appendChild(timeNowDiv)

  this.chart = new google.visualization.LineChart(chartDiv);
  this.chartData = google.visualization.arrayToDataTable([["Time Point", "client"], ["", 0]])
  this.chartData.addRows(60);
  this.chartOptions  = {
    chartArea: {'height': '95%'},
    legend: {position: 'none'},
    vAxis: {
      viewWindow: {
        min:0
      }
    }
  };
  this.chart.draw(this.chartData, this.chartOptions);
}

ChartRenderer.prototype.appendMetric = function(metric) {
  this.addRow(["", metric.value])
}

ChartRenderer.prototype.addRow = function(row) {
  this.chartData.addRow(row)
  this.chartData.removeRow(0);
  this.chart.draw(this.chartData, this.chartOptions);
}