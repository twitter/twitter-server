window.onload = function () {
  var confirmationLinks = {
    "/abortabortabort": "abort",
    "/quitquitquit": "quit",
    "/admin/shutdown": "shutdown"
  }

  for(var key in confirmationLinks) {
    var elem = document.getElementById(key)
    $(elem).click({name: confirmationLinks[key]}, function(event){
      return confirm("Are you sure you want to " + event.data.name  + "?")
    });
  }

  //highlight the correct page in the sidebar
  var location = window.location.pathname+window.location.search
  var selectedLink = document.getElementById(location)
  document.title = $(selectedLink).find('a')[0].innerHTML
  selectedLink.className += " selected"
}
