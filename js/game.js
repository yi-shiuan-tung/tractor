import $ from 'jquery';
import atmosphere from 'atmosphere.js';

export var setUpGame = function(request, subSocket) {
    let socket = atmosphere;

    request.onMessage = function(response) {
        let message = response.responseBody;

        try {
            let json = JSON.parse(message);
            document.getElementById("messages").innerHTML += '<li>' + JSON.stringify(json) + '</li>';
        } catch (e) {
            console.log("Invalid json: " + message);
        }
    };

    subSocket = socket.subscribe(request);

    $("#set_name").on("click", function() {
        subSocket.push(JSON.stringify({"SET_NAME": {"name": document.getElementById("name").value}}));
    });
    $("#start_game").on("click", function() {
        subSocket.push(JSON.stringify({"START_GAME": {}}));
    });
    $("#forfeit").on("click", function() {
        subSocket.push(JSON.stringify({"FORFEIT": {}}));
    });
    $("#declare").on("click", function(e) {
        subSocket.push(JSON.stringify({"DECLARE": {"cardIds": [document.getElementById("card_id").value]}}));
    });
};