var ip = "212.47.243.140";
var port = "8080";
var startEvalResource = "/stockfish/evaluate";
var getEvalResource = "/stockfish/evaluate";
var clientid = Math.floor(Math.random() * 1000000);
var evalChecker = null;
var fenChecker = null;
var curfen = null;

function startEval() {
  var fenInput = document.getElementsByClassName("fen")[0];
  var fen = fenInput.value;
  var startEvalURL = "http://" + ip + ":" + port + startEvalResource;
  $.post(startEvalURL, { clientid: clientid, fen: fen} );
  if (evalChecker !== null)
    clearInterval(evalChecker);
  evalChecker = setInterval(checkEval, 500);
  curFen = fen;

  var watchersDiv = document.getElementsByClassName("watchers")[0].innerHTML = "";
  var messagesDiv = document.getElementsByClassName("messages_container")[0].innerHTML = "";
}

function checkEval() {
  var getEvalURL = "http://" + ip + ":" + port + getEvalResource;
  var watchersDiv = document.getElementsByClassName("watchers")[0];
  var messagesDiv = document.getElementsByClassName("messages_container")[0];

  $.ajax({
      url: getEvalURL + "?clientid=" + clientid,
      dataType: 'json',
      cache: false,

      beforeSend: function () {
          console.log("Loading");
      },

      error: function (jqXHR, textStatus, errorThrown) {
          console.log(jqXHR);
          console.log(textStatus);
          console.log(errorThrown);
      },

      success: function (data) {
        console.log(data);
        if (data.status !== "found" || data.bestMove === undefined)
          return;
        var evalOutput = "(" + data.eval/100 + ") [" + data.depth + "] " + data.bestMove;
        watchersDiv.innerHTML = evalOutput;
        messagesDiv = evalOutput;

        if(data.running === false)
          clearInterval(evalChecker);
      },

      complete: function () {
          console.log('Finished all tasks');
      }
  });
}

function checkFen() {
  var fenInput = document.getElementsByClassName("fen")[0];
  var fen = fenInput.value;
  if (fen !== curFen)
    startEval();
}

/* Listen for messages */
chrome.runtime.onMessage.addListener(function(msg, sender, sendResponse) {
  if (msg.command && (msg.command == "add_engine")) {
    startEval();
    fenChecker = setInterval(checkFen, 500);
  }
});
