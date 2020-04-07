import * as React from "react";
import { getCardImageSrc } from "./assets";
import { setUpConnection } from "./connection";
import "./game.css";

const WIDTH = 800;
const HEIGHT = 600;

const CARD_WIDTH = 71;
const CARD_HEIGHT = 96;

export class Game extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            myId: undefined,
            inputMyName: '',
            playerNames: {}, // { playerId: playerName }
            gameStatus: 'START_ROUND',
            myHand: [], // Card[]
            selectedCardIds: {}, // { cardId: boolean }
            declaringPlayerId: undefined,
            declaredCards: [],
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
            } else if (json.DECLARE) {
                const { playerId, cards } = json.DECLARE;
                this.setState({ declaringPlayerId: playerId, declaredCards: cards });
            } else {
                console.log("Unhandled message: " + JSON.stringify(json));
            }
        });
    }

    componentWillUnmount() {
        this.subSocket.disconnect();
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
            <div className="game_area" style={{ width: `${WIDTH}px`, height: `${HEIGHT}px` }}>
                {this.renderMyHand()}
                {this.renderDeclaredCards()}
            </div>
        );
    }

    renderMyHand() {
        const { gameStatus, myHand, selectedCardIds, declaredCards } = this.state;
        return myHand

            // If not playing tricks, declared cards should be shown in front, not in hand
            .filter(card => gameStatus === 'PLAY' || declaredCards.every(declaredCard => card.id !== declaredCard.id))

            .map((card, index) => {
                const x = 25 + index * 15;
                const y = selectedCardIds[card.id] ? 380 : 400;
                return (
                    <img
                        key={card.id}
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

    renderDeclaredCards() {
        const { declaredCards } = this.state;
        const startX = WIDTH / 2 - (71 + 25 * (declaredCards.length - 1)) / 2;
        return declaredCards
            .map((card, index) => {
                const x = startX + 25 * index;
                const y = 250;
                return (
                    <img
                        key={card.id}
                        style={
                            {
                                top: `${y}px`,
                                left: `${x}px`,
                            }
                        }
                        src={getCardImageSrc(card)}
                    />
                );
            });
    }
}
