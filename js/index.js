import $ from 'jquery';
import atmosphere from 'atmosphere.js';

$(function() {

    let socket = atmosphere;
    let subSocket;
    let transport = 'websocket';


    let request = {
        url: document.location.toString() + "tractor",
        contentType : "application/json",
        logLevel : 'debug',
        transport : 'websocket' ,
        fallbackTransport: 'long-polling'
    };

    request.onOpen = function(response) {
        transport = response.transport;

        // Carry the UUID. This is required if you want to call subscribe(request) again.
        request.uuid = response.request.uuid;
    };

    request.onClientTimeout = function(r) {
        subSocket.push(JSON.stringify({ author: author, message: 'is inactive and closed the connection. Will reconnect in ' + request.reconnectInterval }));
        setTimeout(function (){
            subSocket = socket.subscribe(request);
        }, request.reconnectInterval);
    };

    request.onReopen = function(response) {
        console.log('Atmosphere re-connected using ' + response.transport);
    };

    request.onMessage = function(response) {
        console.log("Getting message: " + response.responseBody);
        let message = response.responseBody;

        try {
            let json = JSON.parse(message);
            document.getElementById("messages").innerHTML += '<li>' + JSON.stringify(json) + '</li>';
        } catch (e) {
            console.log("Invalid json: " + message);
        }
    };

    request.onError = console.error;

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

});
