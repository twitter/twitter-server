function ChartRenderer(element, title) {
  var header = $("<h5></h5>")
    .addClass("text-center")
    .text(title)
  var chartDiv = $("<div></div>")[0]

  $(element).empty().append(header)
  $(element).append(chartDiv)

  this.chart = new google.visualization.LineChart(chartDiv)
  this.chartData = new google.visualization.DataTable()
  this.chartData.addColumn('datetime', 'Time')
  this.chartData.addColumn('number', title)
  this.chartData.addRows(60)
  this.chartOptions = {
    legend: { position: 'none' },
    width: "100%",
    height: 350,
    chartArea: { width: "90%", height: "85%" },
    vAxis: {
      viewWindow: { min:0 },
      baselineColor: '#ddd',
      gridlineColor: '#ddd'
    },
    hAxis: {
      baselineColor: '#ddd',
      gridlineColor: '#ddd',
      textStyle: {
        fontSize: 12
      }
    },
  }

  this.chart.draw(this.chartData, this.chartOptions)
}

ChartRenderer.prototype.appendMetric = function(metric) {
  this.addRow([new Date(), metric.delta])
}

ChartRenderer.prototype.addRow = function(row) {
  this.chartData.addRow(row)
  this.chartData.removeRow(0)
  this.chart.draw(this.chartData, this.chartOptions)
}