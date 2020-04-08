import * as React from "react";
import { getCardImageSrc, getFaceDownCardImageSrc } from "./assets";
import { setUpConnection } from "./connection";
import "./game.css";

const WIDTH = 1200;
const HEIGHT = 800;

const CARD_WIDTH = 71;
const CARD_HEIGHT = 96;

export class Game extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            // local state
            inputMyName: '',
            playerNames: {}, // { playerId: playerName }
            selectedCardIds: {}, // { cardId: boolean }

            // game state
            playerIds: [], // PlayerId[]
            status: 'START_ROUND', // GameStatus
            currentPlayerIndex: undefined, // integer
            isDeclaringTeam: undefined, // { playerId: boolean }
            deck: undefined, // cardId[]
            cardsById: undefined, // { cardId: Card }
            playerHands: undefined, // { playerId: cardId[] }
            declaredCards: undefined, // Play[]
            kitty: undefined, // Card[]
            pastTricks: undefined, // Trick[]
            currentTrick: undefined, // Trick
        }
    }

    componentDidMount() {
        const { roomCode } = this.props;
        this.subSocket = setUpConnection(document.location.toString() + "tractor/" + roomCode, response => this.myId = response.request.uuid, response => {
            const { cardsById } = this.state;

            let message = response.responseBody;
            console.log("Received message: " + message);

            let json = JSON.parse(message);

            if (json.WELCOME) {
                const { playerNames } = json.WELCOME;
                this.setState({ playerNames });
            } else if (json.UPDATE_PLAYERS) {
                const { playerIds, playerNames } = json.UPDATE_PLAYERS;
                this.setState({ playerIds, playerNames });
            } else if (json.START_ROUND) {
                this.setState({ ...json.START_ROUND });
            } else if (json.CARD_INFO) {
                this.setState({ cardsById: {
                    ...cardsById,
                    ...json.CARD_INFO.cardsById,
                }});
            } else if (json.DRAW) {
                this.setState({ ...json.DRAW });
            } else if (json.DECLARE) {
                this.setState({ ...json.DECLARE });
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
        const { inputMyName, playerIds, playerNames, selectedCardIds } = this.state;
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
                        {playerIds.map(playerId => <li key={playerId}>{playerNames[playerId]}{playerId === this.myId ? " (me)" : ""}</li>)}
                    </ul>
                </div>
                <div>
                    <button type="button" onClick={() => {
                        this.subSocket.push(JSON.stringify({ "START_ROUND": {} }));
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
        const { status } = this.state;
        if (status === 'START_ROUND') {
            return;
        }
        return (
            <div className="game_area" style={{ width: `${WIDTH}px`, height: `${HEIGHT}px` }}>
                {this.renderPlayerHands()}
                {this.renderDeclaredCards()}
            </div>
        );
    }

    renderPlayerHands() {
        const { selectedCardIds, status, playerIds, cardsById, playerHands, declaredCards } = this.state;
        const myIndex = playerIds.indexOf(this.myId);
        return playerIds.map((playerId, index) => {
            const hand = playerHands[playerId];
            const interCardDistance = playerId === this.myId ? 15 : 8;
            const totalWidth = CARD_WIDTH + interCardDistance * (hand.length - 1);

            const cardImgs = playerHands[playerId]

                // If not playing tricks, declared cards should be shown in front, not in hand
                .filter(
                    cardId => status === 'PLAY'
                        || declaredCards.length === 0
                        || declaredCards[declaredCards.length - 1].cardIds.every(declaredCardId => cardId !== declaredCardId))

                .map((cardId, index) => {
                    const x = -totalWidth / 2 + interCardDistance * index;
                    const y = selectedCardIds[cardId] ? -20 : 0;
                    let src = getFaceDownCardImageSrc();
                    let onClick = undefined;
                    if (playerId === this.myId) {
                        src = getCardImageSrc(cardsById[cardId]);
                        onClick = () => this.setState({
                            selectedCardIds: {
                                ...selectedCardIds,
                                [cardId]: !selectedCardIds[cardId],
                            }
                        });
                    }
                    return (
                        <img
                            key={cardId}
                            style={
                                {
                                    top: `${y}px`,
                                    left: `${x}px`,
                                }
                            }
                            src={src}
                            onClick={onClick}
                        />
                    );
                });

            const centerPoint = {
                x: WIDTH * (.5 + Math.sin((index - myIndex) * 2 * Math.PI / playerIds.length) * .3),
                y: HEIGHT * (.5 + Math.cos((index - myIndex) * 2 * Math.PI / playerIds.length) * .3),
            };
            const angle = (myIndex - index) * 360. / playerIds.length;
            return (
                <div
                    key={playerId}
                    className="player_container"
                    style={
                        {
                            top: centerPoint.y,
                            left: centerPoint.x,
                            transform: `rotate(${angle}deg)`,
                        }
                    }>
                    {cardImgs}
                </div>
            )
        });
    }

    renderDeclaredCards() {
        const { cardsById, declaredCards } = this.state;
        if (declaredCards.length === 0) {
            return;
        }
        const cardIds = declaredCards[declaredCards.length - 1].cardIds;
        const startX = WIDTH / 2 - (71 + 25 * (cardIds.length - 1)) / 2;
        return cardIds
            .map((cardId, index) => {
                const x = startX + 25 * index;
                const y = 250;
                return (
                    <img
                        key={cardId}
                        style={
                            {
                                top: `${y}px`,
                                left: `${x}px`,
                            }
                        }
                        src={getCardImageSrc(cardsById[cardId])}
                    />
                );
            });
    }
}
