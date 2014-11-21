function MsToStringConverter() {
  this.msInSecond = 1000
  this.msInMinute = this.msInSecond*60
  this.msInHour = this.msInMinute*60
  this.msInDay = this.msInHour*24
  this.msInYear = this.msInDay*365.242
}

MsToStringConverter.prototype.showMilliseconds = function(ms) { return ms + "ms"}
MsToStringConverter.prototype.showSeconds = function(seconds) { return seconds + "s" }
MsToStringConverter.prototype.showMinutes= function(minutes) { return minutes + "m" }
MsToStringConverter.prototype.showHours = function(hours) { return hours + "h" }
MsToStringConverter.prototype.showDays = function(days) { return days + "d" }
MsToStringConverter.prototype.showYears = function(years) { return years + "y" }

MsToStringConverter.prototype.convert = function(ms) {
  var years = (ms/this.msInYear).toFixed(0)
  var days = Math.floor(((ms - this.msInYear*years)/this.msInDay))
  var hours = Math.floor((ms - this.msInYear*years - this.msInDay*days)/this.msInHour)
  var minutes = Math.floor((ms - this.msInYear*years - this.msInDay*days - this.msInHour*hours)/this.msInMinute)
  var seconds = Math.floor((ms - this.msInYear*years - this.msInDay*days - this.msInHour*hours - this.msInMinute*minutes)/this.msInSecond)

  if(ms < this.msInSecond) return this.showMilliseconds(ms)
  else if(ms < this.msInMinute) return this.showSeconds(seconds)
  else if(ms < this.msInHour) return this.showMinutes(minutes) + " " + this.showSeconds(seconds)
  else if(ms < this.msInDay) return this.showHours(hours) + " " + this.showMinutes(minutes) + " " + this.showSeconds(seconds)
  else if(ms < this.msInYear) return this.showDays(days) + " " + this.showHours(hours) + " " + this.showMinutes(minutes)
  else return this.showYears(years) + " " + this.showDays(days) + " " + this.showHours(hours)
}

function BytesToStringConverter() {
  this.bytesInKB = 1024
  this.bytesInMB = this.bytesInKB*1024
  this.bytesInGB = this.bytesInMB*1024
}

BytesToStringConverter.prototype.showB = function(b) { return b + "B"}
BytesToStringConverter.prototype.showKB = function(kb) { return kb + "KB"}
BytesToStringConverter.prototype.showMB = function(mb) { return mb + "MB"}
BytesToStringConverter.prototype.showGB = function(gb) { return gb + "GB"}

BytesToStringConverter.prototype.convert = function(b) {
  if(b < this.bytesInKB) return this.showB(b)
  else if(b < this.bytesInMB) return this. showKB((b/this.bytesInKB).toFixed(1))
  else if(b < this.bytesInGB) return this.showMB((b/this.bytesInMB).toFixed(1))
  else return this.showGB((b/this.bytesInGB).toFixed(1))
}