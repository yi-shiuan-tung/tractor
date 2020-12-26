import PropTypes from 'prop-types';
import * as React from 'react';
import { animated, interpolate } from 'react-spring';
import AnimatedItems from '../../lib/animatedItems';
import { getAudio } from '../../lib/audio';
import { SUITS } from '../../lib/cards';
import { getCardImageSrc, getFaceDownCardImageSrc, preloadCardImages } from '../../lib/cardImages';
import {setUpConnection} from '../../lib/connection';
import {
  ActionButton,
  CenteredText,
  ConfirmationPanel,
  FindAFriendPanel,
  GameInfoPanel,
  HoverButton,
  PlayerNametag,
  RoundInfoPanel,
  RoundStartPanel,
  SettingsPanel,
} from '../../components';
import './room.css';

const WIDTH = 1200;
const HEIGHT = 800;
const CARD_WIDTH = 71;
const CARD_HEIGHT = 100;
const CROWN_WIDTH = 48;
const CROWN_HEIGHT = 48;

export class Room extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      // room state (same as server)
      humanControllers: [], // PlayerId[]
      aiControllers: [], // PlayerId[]
      playerNames: {}, // {playerId: playerName}
      playerReadyForPlay: {}, // {playerId: boolean}
      myPlayerId: undefined, // PlayerId

      // local state
      selectedCardIds: {}, // {cardId: boolean}
      notifications: {},
      showPreviousTrick: false,
      confirmSpecialPlayCards: undefined, // CardId[]?
      soundVolume: 1, // 0, 1, 2, or 3
      isEditingPlayers: false, // boolean
      showKitty: false, // boolean
      localName: undefined, // string

      // game state (same as server)
      playerIds: [], // PlayerId[]
      numDecks: 2, // integer
      findAFriend: false, // boolean
      kittySize: 8, // integer
      roundNumber: undefined, // integer
      starterPlayerIndex: undefined, // integer
      playerRankScores: {}, // {playerId: cardValue}
      winningPlayerIds: [], // PlayerId[]
      status: 'START_ROUND', // GameStatus
      currentPlayerIndex: undefined, // integer
      isDeclaringTeam: undefined, // {playerId: boolean}
      deck: undefined, // cardId[]
      cardsById: undefined, // {cardId: Card}
      playerHands: undefined, // {playerId: cardId[]}
      declaredCards: undefined, // Play[]
      exposedBottomCards: undefined, // cardId[]
      kitty: undefined, // cardId[]
      findAFriendDeclaration: undefined, // FindAFriendDeclaration
      pastTricks: undefined, // Trick[]
      currentTrick: undefined, // Trick
      currentRoundScores: undefined, // {playerId: integer}
      currentRoundPenalties: undefined, // {playerId: integer}
      currentTrump: undefined, // Card
    };
  }

  componentDidMount() {
    this.audio = getAudio();
    preloadCardImages();
    this.joinRoomWebsocket();
    this.setState({ localName: window.localStorage.getItem('name') });
  }

  componentDidUpdate(prevProps, prevState) {
    if (this.props.roomCode !== prevProps.roomCode) {
      this.connection.disconnect();
      this.joinRoomWebsocket();
    }

    const {
      aiControllers,
      humanControllers,
      playerNames,
      myPlayerId,
      localName,
      playerIds,
    } = this.state;
    if (!myPlayerId) {
      const unmappedPlayerIds = playerIds
        .filter(playerId => aiControllers.indexOf(playerId) === -1 && humanControllers.indexOf(playerId) === -1);
      if (localName && unmappedPlayerIds.length > 0) {
        this.setState({ myPlayerId: "pending" });
        this.connection.send({
          REJOIN: { playerId: unmappedPlayerIds.filter(playerId => playerNames[playerId] === localName)[0] }
        });
      }
    } else if (myPlayerId !== prevState.myPlayerId || playerNames[myPlayerId] != prevState.playerNames[myPlayerId]) {
      if (localName && playerNames[myPlayerId] !== localName) {
        this.connection.send({ SET_NAME: { name: localName } });
      }
    }
  }

  componentWillUnmount() {
    this.connection.disconnect();
  }

  joinRoomWebsocket() {
    const { roomCode, leaveRoom } = this.props;
    this.setState({ status: 'START_ROUND' });
    this.connection = setUpConnection(
        '/' + roomCode,
        json => {
          const {playerNames, myPlayerId, playerIds, status, cardsById} = this.state;

          if (json.LEAVE_ROOM) {
            leaveRoom();
          } else if (json.ROOM_STATE) {
            this.setState(json.ROOM_STATE);
          } else if (json.REJOIN) {
            this.setState(json.REJOIN);
          } else if (json.UPDATE_PLAYERS) {
            this.setState(json.UPDATE_PLAYERS);
          } else if (json.UPDATE_AIS) {
            this.setState(json.UPDATE_AIS);
          } else if (json.GAME_CONFIGURATION) {
            this.setState(json.GAME_CONFIGURATION);
          } else if (json.START_ROUND) {
            this.setState(json.START_ROUND);
            this.audio.playBackground();
          } else if (json.CARD_INFO) {
            this.setState({cardsById: {
              ...cardsById,
              ...json.CARD_INFO.cardsById,
            }});
          } else if (json.DRAW) {
            this.setState(json.DRAW);
          } else if (json.DECLARE) {
            const { playerId, ...other } = json.DECLARE;
            if (playerId === myPlayerId) {
              this.connection.send({ READY_FOR_PLAY: { ready: true } })
            }
            this.setState(other);
          } else if (json.READY_FOR_PLAY) {
            this.setState(json.READY_FOR_PLAY);
          } else if (json.EXPOSE_BOTTOM_CARDS) {
            this.setNotification(`The trump suit is ${SUITS[json.EXPOSE_BOTTOM_CARDS.currentTrump.suit]}`)
            this.setState(json.EXPOSE_BOTTOM_CARDS);
          } else if (json.TAKE_KITTY) {
            this.setState(json.TAKE_KITTY);
          } else if (json.FRIEND_DECLARE) {
            this.setState(json.FRIEND_DECLARE);
            this.audio.slowlyStopBackground();
          } else if (json.MAKE_KITTY) {
            this.setState(json.MAKE_KITTY);
            if (json.MAKE_KITTY.status === 'PLAY') {
              this.audio.slowlyStopBackground();
            }
          } else if (json.PLAY) {
            this.setState(json.PLAY);
            if (status === 'PLAY' && playerIds[json.PLAY.currentPlayerIndex] === myPlayerId) {
              this.audio.playYourTurn();
            }
          } else if (json.FINISH_TRICK) {
            this.setState(json.FINISH_TRICK);
            if (playerIds[json.FINISH_TRICK.currentPlayerIndex] === myPlayerId) {
              this.audio.playYourTurn();
            }
          } else if (json.CONFIRM_SPECIAL_PLAY) {
            const {cardIds} = json.CONFIRM_SPECIAL_PLAY;
            this.setState({confirmSpecialPlayCards: cardIds})
          } else if (json.INVALID_SPECIAL_PLAY) {
            const {playerId, ...other} = json.INVALID_SPECIAL_PLAY;
            this.setNotification(`${playerNames[playerId]} made an invalid special play.`);
            this.setState(other);
          } else if (json.FRIEND_JOINED) {
            const {playerId, ...other} = json.FRIEND_JOINED;
            this.setNotification(`${playerNames[playerId]} has joined the declaring team!`);
            this.setState(other);
          } else if (json.TAKE_BACK) {
            const {playerId, ...other} = json.TAKE_BACK;
            this.setNotification(`${playerNames[playerId]} took back their cards`);
            this.setState(other);
          } else if (json.FORFEIT) {
            const {playerId, message, ...other} = json.FORFEIT;
            this.setNotification(`${playerNames[playerId]} forfeited.`);
            this.setState(other);
          } else if (json.FINISH_ROUND) {
            const isWin = json.FINISH_ROUND.winningPlayerIds.includes(myPlayerId);
            if (isWin) {
              this.setNotification('You win!');
              this.audio.playVictory();
            } else {
              this.setNotification('You lose.');
              this.audio.playDefeat();
            }
            this.setState(json.FINISH_ROUND);
          } else if (json.RECONNECT) {
            const { playerId } = json.RECONNECT;
            this.setNotification(`${playerNames[playerId]} reconnected.`);
          } else if (json.DISCONNECT) {
            const { playerId } = json.DISCONNECT;
            this.setNotification(`${playerNames[playerId]} disconnected.`);
          } else if (json.INVALID_ACTION) {
            this.setNotification(json.INVALID_ACTION.message);
          } else {
            console.error('Unhandled message: ' + JSON.stringify(json));
          }
        });
  }

  setNotification(message) {
    const id = new Date().getTime();
    this.setState({
      notifications: {
        ...this.state.notifications,
        [id]: message,
      },
    });
    // After a brief period, remove this notification (and all notifications
    // before it just in case)
    setTimeout(() => {
      const {notifications} = this.state;
      this.setState({
        notifications: Object.keys(notifications)
            .filter((otherId) => otherId > id)
            .reduce((obj, key) => {
              obj[key] = notifications[key];
              return obj;
            }, {}),
      });
    }, 2000);
  }

  render() {
    const {roomCode} = this.props;
    return (
      <div>
        <div>
          <h3>Room Code: {roomCode}</h3>
        </div>
        {this.renderGameArea()}
      </div>
    );
  }

  renderGameArea() {
    return (
      <div className='game_area'>
        {this.renderRoundStartPanel()}
        {this.renderRoundInfo()}
        {this.renderGameInfo()}
        {this.renderPlayerNames()}
        {this.renderNotifications()}
        {this.renderFindAFriendPanel()}
        {this.renderAnimations()}
        {this.renderSettings()}
        {this.renderActionButton()}
        {this.renderKittyButton()}
        {this.renderLastTrickButton()}
      </div>
    );
  }

  renderRoundStartPanel() {
    const {
      aiControllers,
      humanControllers,
      playerNames,
      playerReadyForPlay,
      myPlayerId,
      isEditingPlayers,
      localName,
      playerIds,
      numDecks,
      findAFriend,
      playerRankScores,
      winningPlayerIds,
      status,
    } = this.state;
    if (status === 'START_ROUND') {
      return <RoundStartPanel
        aiControllers={aiControllers}
        humanControllers={humanControllers}
        playerNames={playerNames}
        playerReadyForPlay={playerReadyForPlay}
        myPlayerId={myPlayerId}
        isEditingPlayers={isEditingPlayers}
        localName={localName}
        playerIds={playerIds}
        numDecks={numDecks}
        findAFriend={findAFriend}
        playerRankScores={playerRankScores}
        winningPlayerIds={winningPlayerIds}
        setPlayerOrder={playerIds => this.connection.send({ PLAYER_ORDER: { playerIds }})}
        setName={this.setName}
        setPlayerScore={(playerId, increment) => this.connection.send({ PLAYER_SCORE: { playerId, increment }})}
        removePlayer={playerId => this.connection.send({ REMOVE_PLAYER: { playerId } })}
        setGameConfiguration={gameConfiguration => this.connection.send({ GAME_CONFIGURATION: gameConfiguration })}
        addAi={() => this.connection.send({ ADD_AI: {} })}
        setReadyForPlay={ready => {
          this.audio.prepare();
          this.connection.send({ READY_FOR_PLAY: { ready }});
        }}
      />;
    }
  }

  renderRoundInfo() {
    const {
      playerNames,
      myPlayerId,
      playerIds,
      starterPlayerIndex,
      isDeclaringTeam,
      findAFriendDeclaration,
      currentRoundScores,
      currentRoundPenalties,
      currentTrump,
    } = this.state;
    return <RoundInfoPanel
      playerNames={playerNames}
      myPlayerId={myPlayerId}
      playerIds={playerIds}
      starterPlayerIndex={starterPlayerIndex}
      isDeclaringTeam={isDeclaringTeam}
      findAFriendDeclaration={findAFriendDeclaration}
      currentRoundScores={currentRoundScores}
      currentRoundPenalties={currentRoundPenalties}
      currentTrump={currentTrump}
    />;
  }

  renderGameInfo() {
    const {playerNames, myPlayerId, playerIds, numDecks, findAFriend, playerRankScores, status} = this.state;
    if (status === 'START_ROUND') {
      return; // all info is already shown in the round start panel
    }
    return <GameInfoPanel
      playerNames={playerNames}
      myPlayerId={myPlayerId}
      playerIds={playerIds}
      numDecks={numDecks}
      findAFriend={findAFriend}
      playerRankScores={playerRankScores}
      status={status}
    />;
  }

  renderPlayerNames() {
    const {
      playerNames,
      myPlayerId,
      playerIds,
      findAFriend,
      status,
      currentPlayerIndex,
      isDeclaringTeam,
      currentRoundScores,
    } = this.state;
    if (status === 'START_ROUND') {
      return;
    }
    return playerIds.map(playerId => {
      const { x, y, angle } = this.getPositioner({
        playerId,
        distance: 0.72,
      })({ x: playerId === myPlayerId ? 240 : 0, y: CARD_HEIGHT / 2 + 30 });

      // ensure text is as close to right-side-up as possible
      let adjustedAngle = angle;
      if (adjustedAngle < 0) {
        adjustedAngle += 360;
      }
      if (adjustedAngle > 90 && adjustedAngle < 270) {
        adjustedAngle -= 180;
      }

      return (
        <CenteredText
          key={playerId}
          x={x}
          y={y}
          angle={adjustedAngle}
        >
          <PlayerNametag
            playerId={playerId}
            playerNames={playerNames}
            playerIds={playerIds}
            findAFriend={findAFriend}
            status={status}
            currentPlayerIndex={currentPlayerIndex}
            isDeclaringTeam={isDeclaringTeam}
            currentRoundScores={currentRoundScores}
          />
        </CenteredText>
      );
    });
  }

  renderNotifications() {
    const {
      playerNames,
      playerReadyForPlay,
      myPlayerId,
      notifications,
      confirmSpecialPlayCards,
      playerIds,
      kittySize,
      status,
      currentPlayerIndex,
    } = this.state;
    if (confirmSpecialPlayCards !== undefined) {
      return <ConfirmationPanel
        message={'That is a multiple-component play. If any component can be beaten, you will pay a 10 point penalty.'}
        confirm={() => {
          this.connection.send({ PLAY: { cardIds: confirmSpecialPlayCards, confirmSpecialPlay: true } });
          this.setState({ confirmSpecialPlayCards: undefined });
        }}
        cancel={() => this.setState({ confirmSpecialPlayCards: undefined })}
      />;
    }
    if (Object.entries(notifications).length > 0) {
      return Object.entries(notifications).map(([id, message]) =>
        <div key={id} className='notification warn'>{message}</div>,
      );
    }
    if (status === 'DRAW') {
      return <div className='notification'>{"Select one or more cards to declare"}</div>
    }
    if (!playerReadyForPlay[myPlayerId] && status === 'DRAW_KITTY') {
      return <div className='notification'>{"Select card(s) to declare, or click Pass"}</div>
    }
    const playerId = playerIds[currentPlayerIndex];
    if (status === 'MAKE_KITTY') {
      if (playerId === myPlayerId) {
        return <div className='notification'>{`Select ${kittySize} cards to put in the kitty`}</div>
      } else {
        return <div className='notification'>{`${playerNames[playerId]} is selecting cards for the kitty`}</div>
      }
    }
    if (status === 'PLAY' && playerId === myPlayerId) {
      return <div className='notification short'>{'Your turn'}</div>
    }
  }

  renderFindAFriendPanel() {
    const { myPlayerId, playerIds, numDecks, starterPlayerIndex, status } = this.state;
    if (status === 'DECLARE_FRIEND' && playerIds[starterPlayerIndex] === myPlayerId) {
      return (
        <FindAFriendPanel
          playerIds={playerIds}
          numDecks={numDecks}
          setFindAFriendDeclaration={declarations => this.connection.send({ FRIEND_DECLARE: { declaration: { declarations } } })}
        />
      );
    }
  }

  renderAnimations() {
    const { cardsById } = this.state;
    const cards = [
      ...this.getMiddleCards(),
      ...this.getPlayerHandCards(),
      ...this.getDeclaredCards(),
      ...this.getBottomCards(),
      ...this.getCurrentTrickCards(),
      ...this.getKittyCards(),
    ];
    const crown = this.getCrown();
    return (
      <div className="animations">
        <AnimatedItems
          items={cards}
          from={_ => { return { opacity: 0 } }}
          update={({ x, y, angle, faceUp, scale }) => ({
            x: x - CARD_WIDTH / 2,
            y: y - CARD_HEIGHT / 2,
            angle,
            faceUp: faceUp ? 1 : 0,
            scale,
            opacity: 1,
          })}
          animated={({ key, cardId, z, selectCards }, { x, y, angle, faceUp, scale, opacity }) => {
            const onClick = selectCards ? () => selectCards(cardId) : undefined;
            return (
              <animated.img
                key={key}
                style={
                  {
                    position: 'absolute',
                    top: y,
                    left: x,
                    transform: interpolate(
                      [angle, faceUp, scale],
                      (angle, faceUp, scale) => `scale(${scale}) rotateY(${faceUp * 180 - (faceUp > 0.5 ? 180 : 0)}deg) rotateZ(${angle}deg)`),
                    zIndex: z,
                    opacity,
                  }
                }
                src={faceUp.interpolate(faceUp => faceUp > 0.5 ? getCardImageSrc(cardsById[cardId]) : getFaceDownCardImageSrc())}
                onClick={onClick}
              />
            );
          }}
        />
        <AnimatedItems
          items={crown}
          from={_ => { return { opacity: 0 } }}
          update={({ x, y, angle }) => ({ x: x - CROWN_WIDTH / 2, y: y - CROWN_HEIGHT / 2, angle, opacity: 1 })}
          animated={({ key }, { x, y, angle, opacity }) => {
            return (
              <animated.div
                key={key}
                className="winner"
                style={
                  {
                    top: y,
                    left: x,
                    transform: angle.interpolate(angle => `rotate(${angle}deg)`),
                    opacity,
                  }
                }
              />
            )
          }}
        />
      </div>
    );
  }

  getMiddleCards() {
    const { myPlayerId, status, deck, pastTricks, currentTrick } = this.state;
    if (status === 'START_ROUND') {
      return [];
    }
    const shownTrick = this.getCurrentTrick();
    const middleCardIds = [...deck];
    for (const trick of [...pastTricks, currentTrick]) {
      if (trick !== shownTrick) {
        middleCardIds.push(...trick.plays.flatMap(play => play.cardIds));
      }
    }
    return this.toCards({
      positioner: this.getPositioner({ playerId: myPlayerId, distance: 0 }),
      cardIds: middleCardIds,
      faceUp: false,
      interCardDistance: 0,
    });
  }

  getPlayerHandCards() {
    const {myPlayerId, selectedCardIds, status, playerIds, playerHands, declaredCards} = this.state;
    if (status === 'START_ROUND') {
      return [];
    }
    return playerIds.flatMap((playerId) => {

      const nonDeclaredCards = playerHands[playerId]
      // If not playing tricks, declared cards should be shown in front,
      // not in hand
          .filter((cardId) => status === 'PLAY' ||
          declaredCards.length === 0 ||
          declaredCards[declaredCards.length - 1].
              cardIds.every((declaredCardId) => cardId !== declaredCardId));

      return this.toCards({
        positioner: this.getPositioner({ playerId, distance: 0.72 }),
        cardIds: nonDeclaredCards,
        faceUp: playerId === myPlayerId,
        interCardDistance: playerId === myPlayerId ? 15 : 9,
        z: myPlayerId === playerId ? 1 : 0,
        selectCards: playerId === myPlayerId ? cardId => this.setState({
          selectedCardIds: {
            ...selectedCardIds,
            [cardId]: !selectedCardIds[cardId],
          },
        }) : undefined,
      });
    });
  }

  getDeclaredCards() {
    const { status, declaredCards } = this.state;
    if (status === 'START_ROUND' ||
      status === 'PLAY' ||
      declaredCards.length === 0) {
      return [];
    }
    const latestDeclaredCards = declaredCards[declaredCards.length - 1];
    return this.toCards({
      positioner: this.getPositioner({ playerId: latestDeclaredCards.playerId, distance: 0.3 }),
      cardIds: latestDeclaredCards.cardIds,
      faceUp: true,
      interCardDistance: 15,
    });
  }

  getBottomCards() {
    const { playerIds, starterPlayerIndex, status, exposedBottomCards } = this.state;
    if (status !== 'EXPOSE_BOTTOM_CARDS') {
      return [];
    }
    return this.toCards({
      positioner: this.getPositioner({ playerId: playerIds[starterPlayerIndex], distance: 0.3 }),
      cardIds: exposedBottomCards,
      faceUp: true,
      interCardDistance: 15,
    });
  }

  getCurrentTrickCards() {
    const currentTrick = this.getCurrentTrick();
    if (!currentTrick) {
      return [];
    }
    return currentTrick.plays.flatMap(({ playerId, cardIds }) => this.toCards({
      positioner: this.getPositioner({ playerId, distance: 0.4}),
      cardIds,
      faceUp: true,
      interCardDistance: 15,
    }));
  }

  getKittyCards() {
    const { myPlayerId, showKitty, playerIds, starterPlayerIndex, status, kitty } = this.state;
    if (status !== 'START_ROUND' && playerIds[starterPlayerIndex] !== myPlayerId) {
      return [];
    }
    if (!kitty || kitty.length === 0) {
      return [];
    }
    return this.toCards({
      positioner: ({ x, y }) => ({
        x: WIDTH - 155 + x,
        y: HEIGHT - (showKitty ? 150 : 55) + y,
        angle: 0,
      }),
      cardIds: kitty,
      faceUp: true,
      interCardDistance: showKitty ? 15 : 0,
      scale: showKitty ? 1 : 0,
    });
  }

  getCrown() {
    const currentTrick = this.getCurrentTrick();
    if (!currentTrick || currentTrick.winningPlayerId === null) {
      return [];
    }
    const {x, y, angle } = this.getPositioner({
      playerId: currentTrick.winningPlayerId,
      distance: 0.4,
    })({x: 0, y: -(CARD_HEIGHT + CROWN_HEIGHT) / 2});
    return [{ key: "crown", x, y, angle }];
  }

  getCurrentTrick() {
    const {showPreviousTrick, status, pastTricks, currentTrick} = this.state;
    if (showPreviousTrick) {
      return pastTricks[pastTricks.length - 1];
    } else if (status !== 'START_ROUND') {
      return currentTrick;
    } else {
      return undefined;
    }
  }

  getPositioner({ playerId, distance }) {
    const { myPlayerId, playerIds} = this.state;
    const playerIndex = playerIds.indexOf(playerId);
    const myIndex = playerIds.indexOf(myPlayerId);
    const centerPoint = {
      x: WIDTH * (.5 + Math.sin((playerIndex - myIndex) * 2 * Math.PI / playerIds.length) * distance / 2 * 0.9),
      y: HEIGHT * (.5 + Math.cos((playerIndex - myIndex) * 2 * Math.PI / playerIds.length) * distance / 2),
    };
    const angle = (myIndex - playerIndex) * 360. / playerIds.length;
    return ({x, y}) => ({
        x: centerPoint.x + Math.cos(Math.PI / 180 * angle) * x - Math.sin(Math.PI / 180 * angle) * y,
        y: centerPoint.y + Math.sin(Math.PI / 180 * angle) * x + Math.cos(Math.PI / 180 * angle) * y,
        angle,
    });
  }

  toCards({positioner, cardIds, faceUp, interCardDistance, z, scale, selectCards}) {
    const { selectedCardIds } = this.state;
    return cardIds.map((cardId, index) => {
      const { x, y, angle } = positioner({
        x: -interCardDistance * (cardIds.length - 1) / 2 + interCardDistance * index,
        y: selectedCardIds && selectedCardIds[cardId] ? -20 : 0,
      });
      return {
        key: `card-${cardId}`,
        cardId,
        x,
        y,
        z: z !== undefined ? z : 0,
        scale: scale !== undefined ? scale : 1,
        angle,
        faceUp,
        selectCards,
      }
    });
  }

  renderSettings() {
    const { myPlayerId, soundVolume, isEditingPlayers, playerIds, status, currentTrick } = this.state;
    return <SettingsPanel
      myPlayerId={myPlayerId}
      soundVolume={soundVolume}
      playerIds={playerIds}
      status={status}
      currentTrick={currentTrick}
      forfeit={() => this.connection.send({ FORFEIT: {} })}
      leaveRoom={() => this.connection.send({ REMOVE_PLAYER: { playerId: myPlayerId } })}
      setSoundVolume={soundVolume => {
        this.audio.setVolume(soundVolume);
        this.setState({ soundVolume });
      }}
      toggleEditPlayers={() => this.setState({ isEditingPlayers: !isEditingPlayers })}
      takeBack={() => this.connection.send({ TAKE_BACK: {} })}
    />;
  }

  renderActionButton() {
    const {
      humanControllers,
      playerNames,
      myPlayerId,
      selectedCardIds,
      playerIds,
      kittySize,
      currentPlayerIndex,
      status,
      declaredCards,
      kitty,
      playerReadyForPlay,
    } = this.state;

    if (playerIds.indexOf(myPlayerId) === -1) {
      return;
    }

    const selectedCardIdsList = Object.entries(selectedCardIds)
        .filter(([_cardId, selected]) => selected)
        .map(([cardId, _selected]) => cardId);
    const iAmReadyForPlay = playerReadyForPlay[myPlayerId];
    const numPlayersReadyForPlay = Object.values(playerReadyForPlay).filter(ready => ready).length;
    const playersNotReadyForPlay = Object.entries(playerReadyForPlay)
      .filter(([_playerId, ready]) => !ready)
      .map(([playerId, _ready]) => playerNames[playerId]);

    if (status === 'DRAW_KITTY' && (selectedCardIdsList.length === 0 || iAmReadyForPlay)) {
      return <ActionButton
        text={`${iAmReadyForPlay ? 'Ready' : 'Pass'} (${numPlayersReadyForPlay}/${humanControllers.length})`}
        clicked={iAmReadyForPlay}
        onClick={() => this.connection.send({ READY_FOR_PLAY: { ready: !iAmReadyForPlay } })}
        title={`Waiting on ${playersNotReadyForPlay.join(', ')}`}
      />;
    }

    if (status === 'DRAW' || (status === 'DRAW_KITTY' && !iAmReadyForPlay)) {
      return <ActionButton
        text='Declare'
        onClick={selectedCardIdsList.length > 0 ? () => {
          const cardIds = [...selectedCardIdsList];

          // if you currently declared cards already, add them as well
          if (declaredCards.length > 0 &&
            declaredCards[declaredCards.length - 1].playerId === myPlayerId) {
            cardIds.push(...declaredCards[declaredCards.length - 1].cardIds);
          }

          this.connection.send({ DECLARE: { cardIds } });
          this.setState({selectedCardIds: {}});
        } : undefined}
      />;
    }

    if (playerIds[currentPlayerIndex] !== myPlayerId) {
      return;
    }

    if (status === 'MAKE_KITTY' && kitty.length === 0) {
      return <ActionButton
        text='Make kitty'
        onClick={selectedCardIdsList.length === kittySize ? () => {
          this.connection.send({ MAKE_KITTY: { cardIds: selectedCardIdsList } });
          this.setState({selectedCardIds: {}});
        } : undefined}
      />;
    }
    if (status === 'PLAY') {
      return <ActionButton
        text='Play'
        onClick={selectedCardIdsList.length > 0 ? () => {
          this.connection.send({ PLAY: { cardIds: selectedCardIdsList } });
          this.setState({selectedCardIds: {}});
        } : undefined}
      />;
    }
  }

  renderKittyButton() {
    const { myPlayerId, playerIds, starterPlayerIndex, status, kitty } = this.state;

    if (status !== 'START_ROUND' && playerIds[starterPlayerIndex] !== myPlayerId) {
      return null;
    }
    if (!kitty || kitty.length === 0) {
      return null;
    }
    return <HoverButton
      className='view_kitty_button'
      onHoverStart={() => this.setState({ showKitty: true })}
      onHoverEnd={() => this.setState({ showKitty: false })}
    />;
  }

  renderLastTrickButton() {
    const {pastTricks} = this.state;
    if (!pastTricks || pastTricks.length === 0) {
      return;
    }
    return <HoverButton
      className='last_trick_button'
      onHoverStart={() => this.setState({showPreviousTrick: true})}
      onHoverEnd={() => this.setState({showPreviousTrick: false})}
    />
  }

  setName = name => {
    this.setState({ localName: name });
    this.connection.send({ SET_NAME: { name } });
    window.localStorage.setItem('name', name);
  }
}

Room.propTypes = {
  roomCode: PropTypes.string,
};
