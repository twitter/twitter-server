/** 
 * @param {string} _title name of the metric
 * @param {string} log_scale scale of the x-axis "true" or "false"
 * @param {string} fmt type of plot: "CDF" or "PDF"
 */
function drawChart(_title, log_scale, fmt) {              
  var options = {
    hAxis: {
      title: "",
      logScale: log_scale
    },
    series: {
      0: { lineWidth: 4 } 
    },
    vAxis: {
      title: fmt,
      titleTextStyle: {
        color: 'black', 
        fontName: 'Helvetica Neue',
        fontSize: 18,
        italic: false,
        bold: false
      },
      ticks: [0, 0.2, 0.4, 0.6, 0.8, 1.0],
      logScale: false
    },
    title: _title,
    titleTextStyle: {
      color: 'black', 
      fontSize: 24,
      fontName: 'Helvetica Neue',
      italic: false,
      bold: true
    },
    legend: {position: 'none'},
    curveType: 'none',
    colors: ['#428bca', '#000000']
  };

  var chart = new google.visualization.AreaChart(document.getElementById('curve_chart'));
  chart.draw(data, options);
}

function setup() {

  // Extracts query parameters from url
  function extractParameters(parameters) {
    var pairs = window.location.search.split("?")[1].split("&");
    for (var i = 0; i < pairs.length; i++) {
      var kv = pairs[i].split("=");
      var key = kv[0];
      var value = kv[1];
      parameters[key] = value;
    }
    if (parameters.log_scale == undefined)
      parameters.log_scale = "false";
    if (parameters.fmt == undefined)
      parameters.fmt = "plot_cdf";
  }

  /** Provide on/off button functionality
   * @param {Object} controller Active field controls on/off
   * @param {string} mod Id of element which functions as the on button
   * @param {string} mod norm of element which functions as the off button
   * @param {function} onModify callback after clicking the on button
   * @param {function} onNormalize callback after clicking the off button
   */
  function colorSwap(controller, mod, norm, onModify, onNormalize) {

    function getBackgroundColor(element) {
      return document.defaultView.getComputedStyle(element, null).getPropertyValue('background-color');
    }

    var modifier = document.getElementById(mod);
    var normal = document.getElementById(norm);
    var onColor = getBackgroundColor(normal);
    var offColor = getBackgroundColor(modifier);

    modifier.onclick = function() {
      if (controller.active === false) {
        modifier.style.background = onColor;
        normal.style.background = offColor;
        controller.active = true;
        if (onModify != undefined) onModify();
      }
    }

    normal.onclick = function() {
      if (controller.active === true) {
        modifier.style.background = offColor;
        normal.style.background = onColor;
        controller.active = false;
        if (onNormalize != undefined) onNormalize();
      }
    }
    if (controller.active === true) {
      modifier.style.background = onColor;
      normal.style.background = offColor;
    } 
  }
  // Fill the params object
  extractParameters(params);

  // Initialize controllers
  shouldRefresh = {active: false};
  colorSwap(shouldRefresh, "refreshOn", "refreshOff", loopWhileRefresh, stopRefresh);

  scaleColorSwitch = {active: params.log_scale == "true"};
  colorSwap(scaleColorSwitch, "log", "reg", 
    function() {params.log_scale = "true"; refresh();},
    function() {params.log_scale = "false"; refresh();}
  )

  formatColorSwitch = {active: params.fmt === "plot_pdf"}
  colorSwap(formatColorSwitch, "PDF", "CDF",
    function() {params.fmt = "plot_pdf"; refresh();},
    function() {params.fmt = "plot_cdf"; refresh();}
  )

  // Add download functionality 
  var downloadLink = document.getElementById("download-link");
  downloadLink.setAttribute("href", generateUrl(extractFormat(params.fmt), "false"));
  downloadLink.download = name + "_" + extractFormat(params.fmt) + 
    "_" + (new Date()).getTime().toString() + ".json";

  // Load google charts library
  google.charts.load('current', {'packages':['corechart']});
  google.charts.setOnLoadCallback(refresh);
}

/** Gives description of axis */
function addColumns() {
  data = new google.visualization.DataTable();
  data.addColumn('number', 'BucketValue');
  data.addColumn('number', 'Proportion');
}

/** 
 * Refreshes the data points.
 * @param {Object} updatesValues Object such as [{bucket: 5, percentage: .5}]
 */
function refreshHistogram(updatedValues) {
  addColumns();
  for (var i = 0; i < updatedValues.length; i++) {
    var p = updatedValues[i];
    var midpoint = (p.lowerLimit + p.upperLimit) / 2;
    data.addRow([midpoint, p.percentage]);
  }
}

/** 
 * Refreshes the statistics table from an object 
 * @param {Object} updatedMetrics Standard finagle performance metrics
 **/
function refreshStatistics(updatedMetrics) {
  var fields = Array("avg", "count", "max", "min", "p50", "p90", "p95", "p99", "p9990", "p9999", "sum");
  var updatedStatistics = {};
  fields.forEach(function(entry) {
    updatedStatistics[entry] = updatedMetrics[params.h + "." + entry];
  })

  function changeText(id, text) {
    document.getElementById(id).innerHTML = text;
  }

  function changeFloat(id, num) {
    changeText(id, num.toFixed(2));
  }
  changeText("P-50", updatedStatistics.p50);
  changeText("P-90", updatedStatistics.p90);
  changeText("P-95", updatedStatistics.p90);
  changeText("P-99", updatedStatistics.p99);
  changeText("P-999", updatedStatistics.p9990);
  changeText("P-9999", updatedStatistics.p9999);
  changeText("Count", updatedStatistics.count);
  changeText("Sum" , updatedStatistics.sum); 
  changeFloat("Avg", updatedStatistics.avg);
  changeText("Min", updatedStatistics.min);
  changeText("Max", updatedStatistics.max);
}