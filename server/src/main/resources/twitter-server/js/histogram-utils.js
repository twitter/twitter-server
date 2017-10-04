/**
 * Generates url to view a histogram 
 * @param {String} fmt The desired plot format (plot_cdf or plot_pdf) 
 * @param {String} log_scale The desired x-axis scale
 */ 
function generateUrl(fmt, log_scale) {
  return window.location.href.split("?")[0] +
    "?h=" + params.h +
    "&fmt=" + encodeURIComponent(fmt) + 
    "&log_scale=" + encodeURIComponent(log_scale);
}

/** 
 * Converts plotting format to json format
 * Ex: plot_cdf -> cdf
 */
function extractFormat(fmt) {
  return fmt.split("_")[1];
}
