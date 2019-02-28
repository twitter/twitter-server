// Storage for data and parameters 
var data;
var params = {};
// Button controllers
var shouldRefresh;
var scaleColorSwitch;
var formatColorSwitch;
// Used to control refreshing
var timeOutCall;
// Create button functionality and 
// load initial data
setup();

/** Requests json encoded histogram counts */
function histogramRefreshRequest(callback) {
  var xhttp = new XMLHttpRequest();
  xhttp.open("GET", generateUrl(extractFormat(params.fmt), params.log_scale), true);
  xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
  xhttp.onreadystatechange = function() {
    if (xhttp.readyState === 4 && xhttp.status == 200) {
      var histoMap = JSON.parse(xhttp.responseText);
      refreshHistogram(histoMap[params.h]);
      callback();
    } else {
      console.log("Histogram refresh request failed with status code: " + xhttp.status +
        " and with ready state of " + xhttp.readyState);
    }
  }
  xhttp.send();
}

/**
 * Requests json encoded stat counts from
 * "/admin/histograms.json?summary=1&h=<histogram_name>"
 * using the response to update the histogram statistics table.
 */
function statisticsRefreshRequest() {
  var xhttp = new XMLHttpRequest();
  var queryString = "?summary=1&h=" + params.h
  xhttp.open("GET",
    window.location.href.split("histograms")[0] + "histograms.json" + queryString,
    true);
  xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
  xhttp.onreadystatechange = function() {
    if (xhttp.readyState === 4 && xhttp.status == 200) {
      refreshStatistics(JSON.parse(xhttp.responseText));
    } else if (xhttp.status != 200) {
      console.log("Statistics refresh request failed with status code: " + xhttp.status);
    }
  }
  xhttp.send();
}

/** Reload statistics and histogram data */
function refresh() {
  statisticsRefreshRequest();
  histogramRefreshRequest(function() {
    drawChart(params.h, params.log_scale, extractFormat(params.fmt).toUpperCase());
  });
  window.history.pushState('Object', 'Title', generateUrl(params.fmt, params.log_scale));
}

/** Reload data every 5 seconds */
function loopWhileRefresh() {
  var refreshFrequencyMillis = 5000;
  timeOutCall = setInterval(refresh, refreshFrequencyMillis);
}

/** Stop looping */
function stopRefresh() {
  clearInterval(timeOutCall);
}
