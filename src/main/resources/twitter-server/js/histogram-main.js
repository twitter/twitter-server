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
  xhttp.onreadystatechange = function() {
    if (xhttp.readyState === 4 && xhttp.status == 200) {
      refreshHistogram(JSON.parse(xhttp.responseText));
      callback();
    } else {
      console.log("Histogram refresh request failed with status code: " + xhttp.status +
        "and with ready state of " + xhttp.readyState);
    }
  }
  xhttp.open("GET", generateUrl(extractFormat(params.fmt), params.log_scale), true);
  xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
  xhttp.send();
}

/** Requests json encoded stat counts */
function statisticsRefreshRequest() {
  var xhttp = new XMLHttpRequest();
  xhttp.onreadystatechange = function() {
    if (xhttp.readyState === 4 && xhttp.status == 200) {
      refreshStatistics(JSON.parse(xhttp.responseText));
    } else if (xhttp.status != 200 && xhttp.status != 0) {
      console.log("Statistics refresh request failed with status code: " + xhttp.status);
    }
  }
  xhttp.open("GET", window.location.href.split("histograms")[0] + "metrics.json", true);
  xhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
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
  var refreshFrequency = 5000; // milliseconds
  timeOutCall = setInterval(refresh, refreshFrequency);
}

/** Stop looping */
function stopRefresh() {
  clearInterval(timeOutCall);
}
