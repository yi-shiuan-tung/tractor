import atmosphere from 'atmosphere.js';

const LOCATION = document.location.toString().split('#')[0]; // remove fragment

/**
 * Opens a websocket connection to the server using JSON-serialized messages.
 *
 * @param {*} contextPath The context path to append to "ws://hostname/tractor"
 * @param {*} onMessage A callback that takes a single parameter, the
 * JSON-deserialized object sent from the server. Called once for each message.
 *
 * @returns an object containing two fields:
 * send: a function that accepts any object, and will send the
 * JSON-serialized object to the server.
 * disconnect: a nullary function to call to disconnect to the server.
 */
export const setUpConnection = function(urlPath, onMessage) {
  let subSocket;
  const request = {
    url: LOCATION + 'tractor' + urlPath,
    contentType: 'application/json',
    logLevel: 'debug',
    transport: 'websocket',
    fallbackTransport: 'long-polling',
  };

  request.onClientTimeout = function(r) {
    subSocket.push(
        JSON.stringify({
          author: author,
          message: 'is inactive and closed the connection. '+
          'Will reconnect in ' + request.reconnectInterval,
        }),
    );
    setTimeout(function() {
      subSocket = socket.subscribe(request);
    }, request.reconnectInterval);
  };

  request.onReopen = function(response) {
    console.log('Atmosphere re-connected using ' + response.transport);
  };

  request.onClose = function(response) {
    // TODO: send close request?
    console.log('disconnecting');
  };

  request.onError = console.error;

  request.onMessage = response => {
    const responseBody = response.responseBody;
    console.log('Received response: ' + responseBody);
    const message = JSON.parse(responseBody);
    onMessage(message);
  };

  const subscribe = atmosphere.subscribe(request);

  return {
    send: message => subscribe.push(JSON.stringify(message)),
    disconnect: subscribe.disconnect,
  };
};
