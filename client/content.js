var ip = "212.47.243.140";
var port = "8080";
var startEvalResource = "/stockfish/evaluate";
var getEvalResource = "/stockfish/geteval";
var clientid = Math.floor(Math.random() * 1000000);
var evalChecker = null;
var fenChecker = null;
var curFen = null;

function startEval() {
	var fenInput = document.getElementsByClassName("fen")[0];
	var fen = fenInput.value;
	var startEvalURL = "http://" + ip + ":" + port + startEvalResource;
	$.post(startEvalURL, { clientid: clientid, fen: fen} );
	if (evalChecker !== null)
		clearInterval(evalChecker);
	evalChecker = setInterval(checkEval, 500);
	curFen = fen;

	document.getElementsByClassName("messages_container")[0].innerHTML = "";
}

function checkEval() {
	var getEvalURL = "http://" + ip + ":" + port + getEvalResource;
	var messagesDiv = document.getElementsByClassName("messages_container")[0];

	$.ajax({
		url: getEvalURL + "?clientid=" + clientid,
		dataType: 'json',
		cache: false,
		beforeSend: function () {
			/* console.log("Loading"); */
		},

		error: function (jqXHR, textStatus, errorThrown) {
			/*
			console.log(jqXHR);
			console.log(textStatus);
			console.log(errorThrown);
		 */
		},

	success: function (data) {
	// console.log(data);
	if (data.status !== "found" || data.bestMove === undefined)
	return;

	var bestMoveLong = data.bestMove;
	var fromSq = bestMoveLong.substring(0,2);
	var toSq = bestMoveLong.substring(2,4);
	var promotionPiece = null;
	if (bestMoveLong.length > 4) // There has been a promotion
		promotionPiece = bestMoveLong.substring(4,5);
	var chess = new Chess(curFen);
	var bestMoveObj = null
		if (promotionPiece === null)
			bestMoveObj = { from: fromSq, to: toSq };
		else // there has been a promotion
			bestMoveObj = { from: fromSq, to: toSq, promotion: promotionPiece };
	var success = chess.move(bestMoveObj);
	var bestMoveShort = chess.history()[0];

	var evalOutput = "";
	if (success !== null)
		evalOutput = "(" + data.eval + ") [" + data.depth + "] " + bestMoveShort;
	messagesDiv.innerHTML = "<br><p style='font-size:175%; text-align:center'>" + evalOutput + "</p>";

	if(data.running === false)
		clearInterval(evalChecker);
					 },

	complete: function () {
							// console.log('Finished all tasks');
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
