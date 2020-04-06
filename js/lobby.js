import $ from 'jquery';
import atmosphere from 'atmosphere.js';
import {setUpConnection} from "./index";
import {setUpGame} from "./game";

$(function() {
    let socket = atmosphere;
    let [request, subSocket] = setUpConnection(document.location.toString() + "tractor");

    request.onMessage = function(response) {
        let message = response.responseBody;

        try {
            let json = JSON.parse(message);
            document.getElementById("messages").innerHTML += '<li>' + JSON.stringify(json) + '</li>';

            if (json.CREATE_ROOM || json.JOIN_ROOM) {
                let roomCode = json.CREATE_ROOM ? json.CREATE_ROOM.roomCode : json.JOIN_ROOM.roomCode;
                let [roomRequest, roomSubSocket] = setUpConnection(document.location.toString() + "tractor/" + roomCode);
                setUpGame(roomRequest, roomSubSocket);
                document.getElementById("room_code_display").innerText = roomCode;
            } else {
                console.log("Unhandled message: " + JSON.stringify(json));
            }
        } catch (e) {
            console.log("Invalid json in lobby: " + message + "; " + e.message);
        }
    };

    subSocket = socket.subscribe(request);

    $("#join_room").on("click", function() {
        let roomCode = document.getElementById("room_code_input").value;
        subSocket.push(JSON.stringify({"JOIN_ROOM": {"roomCode": roomCode}}));
    });

    $("#create_room").on("click", function() {
        subSocket.push(JSON.stringify({"CREATE_ROOM": {}}));
    });


});
