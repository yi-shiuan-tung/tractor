import * as React from "react";
import { getCardImageSrc } from "./assets";
import { setUpConnection } from "./connection";
import "./game.css";

export class Game extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            myId: undefined,
            inputMyName: '',
            playerNames: {}, // { playerId: playerName }
            myHand: [], // Card[]
            selectedCardIds: {}, // { cardId: boolean }
        }
    }

    componentDidMount() {
        const { roomCode } = this.props;
        this.subSocket = setUpConnection(document.location.toString() + "tractor/" + roomCode, response => {
            const { playerNames, myHand } = this.state;

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
            } else if (json.YOUR_DRAW) {
                const { card } = json.YOUR_DRAW;
                this.setState({ myHand: [...myHand, card] });
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
        const { inputMyName, playerNames, selectedCardIds } = this.state;
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
                    <button type="button" onClick={() => {
                        const cardIds = Object.entries(selectedCardIds)
                            .filter(([_cardId, selected]) => selected)
                            .map(([cardId, _selected]) => cardId);
                        this.subSocket.push(JSON.stringify({ "DECLARE": { cardIds } }));
                        this.setState({ selectedCardIds: {} });
                    }}>
                        {"Declare"}
                    </button>
                </div>
                {this.renderGameArea()}
            </div>
        );
    }

    renderGameArea() {
        return (
            <div className="game_area">
                {this.renderMyHand()}
            </div>
        );
    }

    renderMyHand() {
        const { myHand, selectedCardIds } = this.state;
        return myHand.map((card, index) =>{
            const x = 25 + index * 15;
            const y = selectedCardIds[card.id] ? 380 : 400;
            return (
                <img
                    style={
                        {
                            top: `${y}px`,
                            left: `${x}px`,
                        }
                    }
                    src={getCardImageSrc(card)}
                    onClick={() => this.setState({
                        selectedCardIds: {
                            ...selectedCardIds,
                            [card.id]: !selectedCardIds[card.id],
                        }
                    })}
                />
            );
        });
    }
}
