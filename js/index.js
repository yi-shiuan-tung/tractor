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
            document.getElementById("message").textContent = message;
        } catch (e) {
            console.log("Invalid json: " + message);
        }
    };

    request.onError = console.error;

    subSocket = socket.subscribe(request);

    $("#button").on("click", function() {
        console.log(JSON.stringify({"message": document.getElementById("text").value}));
        subSocket.push(JSON.stringify({"message": document.getElementById("text").value}));
    });

});