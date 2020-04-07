import * as React from "react";
import { setUpConnection } from "./connection";

export class Lobby extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            inputRoomCode: '',
        }
    }

    componentDidMount() {
        const { joinRoom } = this.props;
        this.subSocket = setUpConnection(document.location.toString() + "tractor", response => {
            let message = response.responseBody;
            console.log("Received message: " + message);

            let json = JSON.parse(message);

            if (json.CREATE_ROOM || json.JOIN_ROOM) {
                let roomCode = json.CREATE_ROOM ? json.CREATE_ROOM.roomCode : json.JOIN_ROOM.roomCode;
                joinRoom(roomCode);
            } else {
                console.log("Unhandled message: " + JSON.stringify(json));
            }
        });
    }

    componentWillUnmount() {
        this.subSocket.disconnect();
    }

    render() {
        const { inputRoomCode } = this.state;
        return (
            <div>
                <input
                    type="text"
                    value={inputRoomCode}
                    onChange={e => this.setState({ inputRoomCode: e.target.value })}
                />
                <button
                    type="button"
                    onClick={() => {
                        this.subSocket.push(JSON.stringify({ "JOIN_ROOM": { "roomCode": inputRoomCode } }));
                    }}
                >
                    Join existing game
                </button>
                <button
                    type="button"
                    onClick={() => {
                        this.subSocket.push(JSON.stringify({ "CREATE_ROOM": {} }));
                    }}
                >
                    Create new game
                </button>
            </div>
        );
    }
}
