import * as React from "react";
import { VALUES, getCardImageSrc, getFaceDownCardImageSrc } from "./assets";
import { setUpConnection } from "./connection";
import "./game.css";
import { PlayerArea } from "./playerArea";

export const WIDTH = 1200;
export const HEIGHT = 800;

export const CARD_WIDTH = 71;
export const CARD_HEIGHT = 96;

export class Game extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            // local state
            inputMyName: '',
            playerNames: {}, // { playerId: playerName }
            selectedCardIds: {}, // { cardId: boolean }
            notifications: {},
            showKittyButton: false,
            showPreviousTrick: false,

            // game state
            playerIds: [], // PlayerId[]
            kittySize: 8, // integer
            roundNumber: undefined, // integer
            declarerPlayerIndex: undefined, // integer
            playerRankScores: {}, // { playerId: cardValue }
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
            currentRoundScores: undefined, // { playerId: integer }
            currentTrump: undefined, // Card
        }
    }

    componentDidMount() {
        const { roomCode } = this.props;
        this.subSocket = setUpConnection(document.location.toString() + "tractor/" + roomCode, response => this.myId = response.request.uuid, response => {
            const { playerIds, cardsById } = this.state;

            let message = response.responseBody;
            console.log("Received message: " + message);

            let json = JSON.parse(message);

            if (json.WELCOME) {
                const { playerNames } = json.WELCOME;
                this.setState({ playerNames });
            } else if (json.UPDATE_PLAYERS) {
                this.setState({ ...json.UPDATE_PLAYERS });
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
            } else if (json.TAKE_KITTY) {
                this.setState({ ...json.TAKE_KITTY });
                const playerId = playerIds[json.TAKE_KITTY.currentPlayerIndex];
                this.setNotification(this.state.playerNames[playerId] + " is selecting cards for the kitty");
            } else if (json.YOUR_KITTY) {
                this.setState({ ...json.YOUR_KITTY});
                this.setNotification("Select 8 cards to put in the kitty");
                this.setState({showKittyButton: true});
            } else if (json.MAKE_KITTY) {
                this.setState({ ...json.MAKE_KITTY});
                this.setState({showKittyButton:false});
            } else if (json.PLAY) {
                this.setState({ ...json.PLAY });
            } else if (json.FINISH_TRICK) {
                this.setState({ ...json.FINISH_TRICK });
            } else if (json.INVALID_ACTION) {
                this.setNotification(json.INVALID_ACTION.message);
            } else {
                console.log("Unhandled message: " + JSON.stringify(json));
            }
        });
    }

    componentWillUnmount() {
        this.subSocket.disconnect();
    }

    setNotification(message) {
        const id = new Date().getTime();
        this.setState({
            notifications: {
                ...this.state.notifications,
                [id]: message,
            }
        });
        // After a brief period, remove this notification (and all notifications before it just in case)
        setTimeout(() => {
            const { notifications } = this.state;
            this.setState({
                notifications: Object.keys(notifications)
                    .filter(otherId => otherId > id)
                    .reduce((obj, key) => {
                        obj[key] = notifications[key];
                        return obj;
                    }, {})
            });
        }, 2000);
    }

    render() {
        const { roomCode } = this.props;
        const { inputMyName, selectedCardIds } = this.state;
        return (
            <div>
                <div>
                    <h3>Room Code: {roomCode}</h3>
                    {"Name:"}
                    <input type="text" value={inputMyName} onChange={e => this.setState({ inputMyName: e.target.value })} />
                    <button type="button" onClick={() => {
                        this.subSocket.push(JSON.stringify({ "SET_NAME": { "name": inputMyName.slice(0, 20) } }));
                    }}>
                        {"Set my name"}
                    </button>
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
                        this.setState({ selectedCardIds: {} });
                    }}>
                        {"Make Kitty"}
                    </button>
                </div>
                {this.renderGameArea()}
            </div>
        );
    }

    renderGameArea() {
        return (
            <div className="game_area" style={{ width: `${WIDTH}px`, height: `${HEIGHT}px` }}>
                {this.renderRoundInfo()}
                {this.renderGameInfo()}
                {this.renderPlayerNames()}
                {this.renderNotifications()}
                {this.renderPlayerHands()}
                {this.renderDeclaredCards()}
                {this.renderCurrentTrick()}
                {this.renderActionButton()}
                {this.renderLastTrickButton()}
            </div>
        );
    }

    renderRoundInfo() {
        const {
            playerNames,
            playerIds,
            declarerPlayerIndex,
            status,
            isDeclaringTeam,
            currentRoundScores,
            currentTrump,
        } = this.state;
        if (status === 'START_ROUND') {
            return;
        }
        const declarer = playerIds[declarerPlayerIndex] === this.myId
            ? <span className="me">{"You"}</span> : playerNames[playerIds[declarerPlayerIndex]];
        const trumpSuit = currentTrump.suit === 'JOKER' ? 'NO TRUMP' : currentTrump.suit + 'S';
        let opponentsPoints = 0;
        playerIds.forEach(playerId => {
            if (!isDeclaringTeam[playerId]) {
                opponentsPoints += currentRoundScores[playerId];
            }
        });
        return (
            <div className="round_info">
                <div>Current trump: {VALUES[currentTrump.value]} of {trumpSuit}</div>
                <div>Declarer: {declarer}</div>
                <div>Opponent's points: {opponentsPoints}</div>
            </div>
        );
    }

    renderGameInfo() {
        const { playerNames, playerIds, playerRankScores, status } = this.state;
        if (status === 'START_ROUND') {
            return;
        }
        return (
            <div className="game_info">
                <div>Player scores:</div>
                <ul>
                    {playerIds.map(playerId => {
                        const name = playerId === this.myId ? <span className="me">{"You"}</span> : playerNames[playerId];
                        return <li
                            key={playerId}
                        >
                            {name}{`: ${VALUES[playerRankScores[playerId]]}`}
                        </li>;
                    })}
                </ul>
            </div>
        )
    }

    renderPlayerNames() {
        const { playerNames, playerIds, playerRankScores, status, currentPlayerIndex } = this.state;
        if (status === 'START_ROUND') {
            return (
                <div className="player_list">
                    <div className="title">Tractor</div>
                    <ul>
                        {playerIds.map(playerId => <li
                            key={playerId}
                            className={playerId === this.myId ? "me" : ""}
                        >
                            <i className={(playerId !== this.myId || playerIds.indexOf(this.myId) === 0) ? "hidden" : "arrow up"}
                                onClick={() => {
                                    const index = playerIds.indexOf(this.myId);
                                    [playerIds[index], playerIds[index-1]] = [playerIds[index-1], playerIds[index]];
                                    this.subSocket.push(JSON.stringify({"PLAYER_ORDER": { playerIds}}))
                                }}/>
                            <i className={(playerId !== this.myId || playerIds.indexOf(this.myId) === playerIds.length - 1) ? "hidden" : "arrow down"}
                                onClick={() => {
                                    const index = playerIds.indexOf(this.myId);
                                    [playerIds[index], playerIds[index+1]] = [playerIds[index+1], playerIds[index]];
                                    this.subSocket.push(JSON.stringify({"PLAYER_ORDER": { playerIds}}))
                                }}/>
                            {`${playerNames[playerId]} (${VALUES[playerRankScores[playerId]]})`}
                        </li>)}
                    </ul>
                    <div
                        className="action_button start_action_button"
                        onClick={() => this.subSocket.push(JSON.stringify({ "START_ROUND": {} }))}
                    >
                        {"Start game"}
                    </div>
                </div>
            );
        } else {
            return <div>
                {playerIds
                    .filter(playerId => playerId !== this.myId)
                    .map(playerId => {
                        let className = "player_name";
                        if (status !== 'DRAW' && playerId === playerIds[currentPlayerIndex]) {
                            className += " current";
                        }
                        return <PlayerArea
                            playerIds={playerIds}
                            playerId={playerId}
                            myId={this.myId}
                            distance={0.91}
                            isText={true}
                        >
                            <div className={className}>{playerNames[playerId]}</div>
                        </PlayerArea>;
                    })}
            </div>;
        }
    }

    renderNotifications() {
        const { notifications } = this.state;
        return Object.entries(notifications).map(([id, message]) => <div key={id} className="notification">{message}</div>);
    }

    renderPlayerHands() {
        const { status, playerIds, playerHands, declaredCards } = this.state;
        if (status === 'START_ROUND') {
            return;
        }
        return playerIds.map(playerId => {
            const nonDeclaredCards = playerHands[playerId]
                // If not playing tricks, declared cards should be shown in front, not in hand
                .filter(cardId => status === 'PLAY'
                    || declaredCards.length === 0
                    || declaredCards[declaredCards.length - 1].cardIds.every(declaredCardId => cardId !== declaredCardId));

            return (
                <PlayerArea
                    playerIds={playerIds}
                    playerId={playerId}
                    myId={this.myId}
                    distance={0.6}
                >
                    {this.renderCards(nonDeclaredCards, {
                        interCardDistance: playerId === this.myId ? 15 : 9,
                        faceUp: playerId === this.myId,
                        canSelect: playerId === this.myId,
                    })}
                </PlayerArea>
            );
        });
    }

    renderDeclaredCards() {
        const { playerIds, status, declaredCards } = this.state;
        if (status === 'START_ROUND' || status === 'PLAY' || declaredCards.length === 0) {
            return;
        }
        const latestDeclaredCards = declaredCards[declaredCards.length - 1];
        return <div>
            <PlayerArea
                playerIds={playerIds}
                playerId={latestDeclaredCards.playerId}
                myId={this.myId}
                distance={0.3}
            >
                {this.renderCards(latestDeclaredCards.cardIds, {
                    interCardDistance: 15,
                    faceUp: true,
                    canSelect: false,
                })}
            </PlayerArea>
        </div>;
    }

    renderCurrentTrick() {
        const { showPreviousTrick, playerIds, pastTricks, currentTrick } = this.state;
        if (!currentTrick) {
            return;
        }
        if (showPreviousTrick && pastTricks.length > 0) {
            return <div>
                {pastTricks[pastTricks.length - 1].plays.map(({ playerId, cardIds }) => <PlayerArea
                    playerIds={playerIds}
                    playerId={playerId}
                    myId={this.myId}
                    distance={0.2}
                >
                    {this.renderCards(cardIds, {
                        interCardDistance: 15,
                        faceUp: true,
                        canSelect: false,
                    })}
                </PlayerArea>)}
            </div>
        }
        return <div>
            {currentTrick.plays.map(({ playerId, cardIds }) => <PlayerArea
                playerIds={playerIds}
                playerId={playerId}
                myId={this.myId}
                distance={0.2}
            >
                {this.renderCards(cardIds, {
                    interCardDistance: 15,
                    faceUp: true,
                    canSelect: false,
                })}
            </PlayerArea>)}
        </div>;
    }

    renderActionButton() {
        const { selectedCardIds, playerIds, currentPlayerIndex, status, declaredCards } = this.state;
        const selectedCardIdsList = Object.entries(selectedCardIds)
            .filter(([_cardId, selected]) => selected)
            .map(([cardId, _selected]) => cardId);
        if ((status === 'DRAW' || status === 'DRAW_KITTY') && selectedCardIdsList.length > 0) {
            return <div
                className="action_button game_action_button"
                onClick={() => {
                    const cardIds = [...selectedCardIdsList];
                    if (declaredCards.length > 0 && declaredCards[declaredCards.length - 1].playerId === this.myId) {
                        cardIds.push(...declaredCards[declaredCards.length - 1].cardIds);
                    }
                    this.subSocket.push(JSON.stringify({ "DECLARE": { cardIds } }));
                    this.setState({ selectedCardIds: {} });
                }}
            >
                {"Declare"}
            </div>
        }
        if (playerIds[currentPlayerIndex] !== this.myId) {
            return;
        }
        if (status === 'MAKE_KITTY') {
            return <div
                className="action_button game_action_button"
                onClick={() => {
                    this.subSocket.push(JSON.stringify({ "MAKE_KITTY": { cardIds: selectedCardIdsList } }));
                    this.setState({ selectedCardIds: {} });
                }}
            >
                {"Make kitty"}
            </div>
        }
        if (status === 'PLAY') {
            return <div
                className="action_button game_action_button"
                onClick={() => {
                    this.subSocket.push(JSON.stringify({ "PLAY": { cardIds: selectedCardIdsList } }));
                    this.setState({ selectedCardIds: {} });
                }}
            >
                {"Play"}
            </div>
        }
    }

    renderLastTrickButton() {
        const { pastTricks } = this.state;
        if (pastTricks === undefined || pastTricks.length === 0) {
            return;
        }
        return <div
            className="last_trick_button"
            onMouseDown={() => this.setState({ showPreviousTrick: true })}
            onMouseUp={() => this.setState({ showPreviousTrick: false })}
        />
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
