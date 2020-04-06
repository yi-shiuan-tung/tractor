import * as React from "react";
import * as ReactDOM from "react-dom";
import { Lobby } from "./lobby";
import { Game } from "./game";

class App extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            roomCode: undefined,
        };
    }

    render() {
        const { roomCode } = this.state;
        if (roomCode !== undefined) {
            return <Game roomCode={roomCode} />
        } else {
            return <Lobby joinRoom={roomCode => this.setState({ roomCode })}/>
        }
    }
}

ReactDOM.render(<App></App>, document.getElementById('app'));
