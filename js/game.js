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
            notification: undefined,
            showKittyButton: false,

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
            } else if (json.KITTY) {
                const playerId = json.KITTY.playerId;
                this.setState({notification:
                        this.state.playerNames[playerId] + " is selecting cards for the kitty"});
            } else if (json.YOUR_KITTY) {
                this.setState({ ...json.YOUR_KITTY});
                this.setState({notification: "Select 8 cards to put in the kitty", showKittyButton: true});
            } else if (json.INVALID_KITTY) {
                this.setState({notification: json.INVALID_KITTY.message});
            } else if (json.MAKE_KITTY) {
                this.setState({ ...json.MAKE_KITTY});
                this.setState({notification: "", showKittyButton:false});
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
                    {this.state.notification}
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
                    <button type="button" className={this.state.showKittyButton? '':'hidden'} onClick={() => {
                        const cardIds = Object.entries(selectedCardIds)
                            .filter(([_cardId, selected]) => selected)
                            .map(([cardId, _selected]) => cardId);
                        this.subSocket.push(JSON.stringify({ "MAKE_KITTY": { cardIds } }));
                    }}>
                        {"Make Kitty"}
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
                {this.renderActionButton()}
            </div>
        );
    }

    renderPlayerHands() {
        const { status, playerIds, playerHands, declaredCards } = this.state;
        return playerIds.map(playerId => {
            const nonDeclaredCards = playerHands[playerId]
                // If not playing tricks, declared cards should be shown in front, not in hand
                .filter(cardId => status === 'PLAY'
                    || declaredCards.length === 0
                    || declaredCards[declaredCards.length - 1].cardIds.every(declaredCardId => cardId !== declaredCardId));

            const cardImgs = this.renderCards(nonDeclaredCards, {
                interCardDistance: playerId === this.myId ? 15 : 9,
                faceUp: playerId === this.myId,
                canSelect: playerId === this.myId,
            });

            return this.renderPlayerArea(playerId, 0.6, cardImgs);
        });
    }

    renderDeclaredCards() {
        const { declaredCards } = this.state;
        if (declaredCards.length === 0) {
            return;
        }
        const latestDeclaredCards = declaredCards[declaredCards.length - 1];
        const cardImgs = this.renderCards(latestDeclaredCards.cardIds, {
            interCardDistance: 15,
            faceUp: true,
            canSelect: false,
        });
        return this.renderPlayerArea(latestDeclaredCards.playerId, 0.3, cardImgs);
    }

    renderActionButton() {
        const { selectedCardIds, status } = this.state;
        if (Object.values(selectedCardIds).every(selected => !selected)) {
            return;
        }
        if (status === 'DRAW') {
            return <div
                className="action_button"
                onClick={() => {
                    const cardIds = Object.entries(selectedCardIds)
                        .filter(([_cardId, selected]) => selected)
                        .map(([cardId, _selected]) => cardId);
                    this.subSocket.push(JSON.stringify({ "DECLARE": { cardIds } }));
                    this.setState({ selectedCardIds: {} });
                }}
            >
                {"Declare"}
            </div>
        }
        if (status === 'PLAY') {
            return <div
                className="action_button"
                onClick={() => {
                    const cardIds = Object.entries(selectedCardIds)
                        .filter(([_cardId, selected]) => selected)
                        .map(([cardId, _selected]) => cardId);
                    this.subSocket.push(JSON.stringify({ "PLAY": { cardIds } }));
                    this.setState({ selectedCardIds: {} });
                }}
            >
                {"Play"}
            </div>
        }
    }

    /*
     * Renders the given children in front of the given player (under the correct orientation).
     * The distance is a number from 0 (in the middle) to 1 (very close to the player)
     */
    renderPlayerArea(playerId, distance, children) {
        const { playerIds } = this.state;
        const playerIndex = playerIds.indexOf(playerId);
        const myIndex = playerIds.indexOf(this.myId);
        const centerPoint = {
            x: WIDTH * (.5 + Math.sin((playerIndex - myIndex) * 2 * Math.PI / playerIds.length) * distance / 2),
            y: HEIGHT * (.5 + Math.cos((playerIndex - myIndex) * 2 * Math.PI / playerIds.length) * distance / 2),
        };
        const angle = (myIndex - playerIndex) * 360. / playerIds.length;
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
                {children}
            </div>
        );
    }

    renderCards(cardIds, args) {
        const { interCardDistance, faceUp, canSelect } = args;
        const { selectedCardIds, cardsById } = this.state;

        const totalWidth = CARD_WIDTH + interCardDistance * (cardIds.length - 1);
        const cardImgs = cardIds
            .map((cardId, index) => {
                const x = -totalWidth / 2 + interCardDistance * index;
                const y = selectedCardIds[cardId] ? -20 : 0;
                const src = faceUp ? getCardImageSrc(cardsById[cardId]) : getFaceDownCardImageSrc();
                const onClick = canSelect ? () => this.setState({
                    selectedCardIds: {
                        ...selectedCardIds,
                        [cardId]: !selectedCardIds[cardId],
                    }
                }) : undefined;
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
        return <div>{cardImgs}</div>
    }
}
