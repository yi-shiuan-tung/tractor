import * as React from "react";
import { setUpConnection } from "./connection";

export class Game extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            myName: '',
        }
    }

    componentDidMount() {
        const { roomCode } = this.props;
        this.subSocket = setUpConnection(document.location.toString() + "tractor/" + roomCode, response => {
            let message = response.responseBody;
            console.log("Received message: " + message);

            let json = JSON.parse(message);

            if (json.CREATE_ROOM || json.JOIN_ROOM) {
                let roomCode = json.CREATE_ROOM ? json.CREATE_ROOM.roomCode : json.JOIN_ROOM.roomCode;
                this.props.joinRoom(roomCode);
                let [roomRequest, roomSubSocket] = setUpConnection(document.location.toString() + "tractor/" + roomCode);
                setUpGame(roomRequest, roomSubSocket);
                document.getElementById("room_code_display").innerText = roomCode;
            } else {
                console.log("Unhandled message: " + JSON.stringify(json));
            }
        });
    }

    componentWillUnmount() {
        // TODO close subSocket
    }

    render() {
        const { roomCode } = this.props;
        const { myName } = this.state;
        return (
            <div>
                <div>
                    <h3>Room Code: {roomCode}</h3>
                    {"Name:"}
                    <input type="text" value={myName} onChange={e => this.setState({ myName: e.target.value })} />
                    <button type="button" onClick={() => {
                        this.subSocket.push(JSON.stringify({ "SET_NAME": { "name": myName } }));
                    }}>
                        {"Set my name"}
                    </button>
                </div>
                <div>
                    <button type="button" onClick={() => {
                        this.subSocket.push(JSON.stringify({ "START_GAME": {} }));
                    }}>
                        {"Start game"}
                    </button>
                    <button type="button" onClick={() => {
                        this.subSocket.push(JSON.stringify({ "FORFEIT": {} }));
                    }}>
                        {"Forfeit"}
                    </button>
                </div>
            </div>
        );
    }
}
