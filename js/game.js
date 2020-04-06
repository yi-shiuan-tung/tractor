import * as React from "react";
import { setUpConnection } from "./connection";

export class Game extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            myId: undefined,
            inputMyName: '',
            playerNames: {}, // { playerId: playerName }
        }
    }

    componentDidMount() {
        const { roomCode } = this.props;
        this.subSocket = setUpConnection(document.location.toString() + "tractor/" + roomCode, response => {
            const { playerNames } = this.state;

            let message = response.responseBody;
            console.log("Received message: " + message);

            let json = JSON.parse(message);

            if (json.WELCOME) {
                const { playerId, playerNames } = json.WELCOME;
                this.setState({ myId: playerId, playerNames });
            } else if (json.SET_NAME) {
                const { playerId, name } = json.SET_NAME;
                this.setState({ playerNames: {
                    ...playerNames,
                    [playerId]: name,
                }})
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
        const { inputMyName, playerNames } = this.state;
        return (
            <div>
                <div>
                    <h3>Room Code: {roomCode}</h3>
                    {"Name:"}
                    <input type="text" value={inputMyName} onChange={e => this.setState({ inputMyName: e.target.value })} />
                    <button type="button" onClick={() => {
                        this.subSocket.push(JSON.stringify({ "SET_NAME": { "name": inputMyName } }));
                    }}>
                        {"Set my name"}
                    </button>
                </div>
                <div>
                    Players:
                    <ul>
                        {Object.entries(playerNames).map(([id, name]) => <li key={id}>{name}</li>)}
                    </ul>
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
