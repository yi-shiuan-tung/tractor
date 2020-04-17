import atmosphere from 'atmosphere.js';

/**
 * Opens a websocket connection to the server using JSON-serialized messages.
 *
 * @param {*} url The url to connect to. The scheme will be replaced with ws://.
 * @param {*} onMyId A callback that takes a single parameter, the client's
 * unique identifier. Called once.
 * @param {*} onMessage A callback that takes a single parameter, the
 * JSON-deserialized object sent from the server. Called once for each message.
 *
 * @returns an object containing two fields:
 * send: a function that accepts any object, and will send the
 * JSON-serialized object to the server.
 * disconnect: a nullary function to call to disconnect to the server.
 */
export const setUpConnection = function(url, onMyId, onMessage) {
  let subSocket;
  const request = {
    url: url,
    contentType: 'application/json',
    logLevel: 'debug',
    transport: 'websocket',
    fallbackTransport: 'long-polling',
  };

  if (onMyId) {
    request.onOpen = response => onMyId(response.request.uuid);
  }

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
