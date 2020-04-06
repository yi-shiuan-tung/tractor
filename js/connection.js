import atmosphere from 'atmosphere.js';

export var setUpConnection = function(url, onMessage) {

    let subSocket;

    let request = {
        url: url,
        contentType : "application/json",
        logLevel : 'debug',
        transport : 'websocket' ,
        fallbackTransport: 'long-polling'
    };

    request.onOpen = function(response) {
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

    request.onClose = function (response) {
        // TODO: send close request?
        console.log("disconnecting");
    };

    request.onError = console.error;

    request.onMessage = onMessage;

    return atmosphere.subscribe(request);
};
