import PropTypes from 'prop-types';
import * as React from 'react';
import { getAudio } from '../../lib/audio';
import { preloadCardImages } from '../../lib/cardImages';
import {setUpConnection} from '../../lib/connection';
import './room.css';

import {
  ActionButton,
  Cards,
  ConfirmationPanel,
  FindAFriendPanel,
  GameInfoPanel,
  Kitty,
  PlayerArea,
  PlayerNametag,
  RejoinPanel,
  RoundInfoPanel,
  RoundStartPanel,
  SettingsPanel,
  Trick,
} from '../../components';
import { SUITS } from '../../lib/cards';

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
      soundVolume: 3, // 0, 1, 2, or 3
      isEditingPlayers: false, // boolean

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
  }

  componentDidUpdate(prevProps) {
    if (this.props.roomCode !== prevProps.roomCode) {
      this.connection.disconnect();
      this.joinRoomWebsocket();
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
            this.audio.playGameStart();
          } else if (json.CARD_INFO) {
            this.setState({cardsById: {
              ...cardsById,
              ...json.CARD_INFO.cardsById,
            }});
          } else if (json.DRAW) {
            this.setState(json.DRAW);
          } else if (json.DECLARE) {
            this.setState(json.DECLARE);
          } else if (json.READY_FOR_PLAY) {
            this.setState(json.READY_FOR_PLAY);
          } else if (json.EXPOSE_BOTTOM_CARDS) {
            this.setNotification(`The trump suit is ${SUITS[json.EXPOSE_BOTTOM_CARDS.currentTrump.suit]}`)
            this.setState(json.EXPOSE_BOTTOM_CARDS);
          } else if (json.TAKE_KITTY) {
            this.setState(json.TAKE_KITTY);
          } else if (json.FRIEND_DECLARE) {
            this.setState(json.FRIEND_DECLARE);
          } else if (json.MAKE_KITTY) {
            this.setState(json.MAKE_KITTY);
          } else if (json.PLAY) {
            this.setState(json.PLAY);
            if (status === 'PLAY' && playerIds[json.PLAY.currentPlayerIndex] === myPlayerId) {
              this.audio.playYourTurn();
            }
          } else if (json.FINISH_TRICK) {
            const {doDeclarersWin, ...other} = json.FINISH_TRICK;
            this.setState(other);
            if (other.status === 'START_ROUND') {
              this.setNotification(
                 doDeclarersWin ? 'Declarers win!' : 'Opponents win!',
              );
              this.audio.playGameOver();
            } else if (other.status === 'PLAY' && playerIds[other.currentPlayerIndex] === myPlayerId) {
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
            this.setNotification(`${playerNames[playerId]} ${message}.`);
            this.setState(other);
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
        {this.renderPlayerHands()}
        {this.renderDeclaredCards()}
        {this.renderBottomCards()}
        {this.renderCurrentTrick()}
        {this.renderSettings()}
        {this.renderActionButton()}
        {this.renderKitty()}
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
        playerIds={playerIds}
        numDecks={numDecks}
        findAFriend={findAFriend}
        playerRankScores={playerRankScores}
        winningPlayerIds={winningPlayerIds}
        setPlayerOrder={playerIds => this.connection.send({ PLAYER_ORDER: { playerIds }})}
        setName={name => this.connection.send({ SET_NAME: { name }})}
        setPlayerScore={(playerId, increment) => this.connection.send({ PLAYER_SCORE: { playerId, increment }})}
        removePlayer={playerId => this.connection.send({ REMOVE_PLAYER: { playerId } })}
        setGameConfiguration={gameConfiguration => this.connection.send({ GAME_CONFIGURATION: gameConfiguration })}
        addAi={() => this.connection.send({ ADD_AI: {} })}
        setReadyForPlay={ready => this.connection.send({ READY_FOR_PLAY: { ready }})}
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
    const {playerNames, myPlayerId, playerIds, playerRankScores, status} = this.state;
    if (status === 'START_ROUND') {
      return; // all info is already shown in the round start panel
    }
    return <GameInfoPanel
      playerNames={playerNames}
      myPlayerId={myPlayerId}
      playerIds={playerIds}
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
      return <PlayerArea
        key={`playerName${playerId}`}
        myPlayerId={myPlayerId}
        playerIds={playerIds}
        playerId={playerId}
        distance={0.91}
        shiftX={playerId === myPlayerId ? 204 : 0}
        isText={true}
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
      </PlayerArea>;
    });
  }

  renderNotifications() {
    const {
      aiControllers,
      humanControllers,
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
    if (!myPlayerId) {
      return <RejoinPanel
        aiControllers={aiControllers}
        humanControllers={humanControllers}
        playerNames={playerNames}
        playerIds={playerIds}
        rejoin={playerId => this.connection.send({ REJOIN: { playerId }})}
      />;
    }
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
      return <div className='notification'>{"Select card(s) to declare, or press Ready"}</div>
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
    const { myPlayerId, playerIds, findAFriend, starterPlayerIndex, status, findAFriendDeclaration } = this.state;
    if (findAFriend && status === 'MAKE_KITTY' && playerIds[starterPlayerIndex] === myPlayerId && !findAFriendDeclaration) {
      return (
        <FindAFriendPanel
          playerIds={playerIds}
          setFindAFriendDeclaration={declarations => this.connection.send({ FRIEND_DECLARE: { declaration: { declarations } } })}
        />
      );
    }
  }

  renderPlayerHands() {
    const {myPlayerId, selectedCardIds, status, playerIds, cardsById, playerHands, declaredCards} = this.state;
    if (status === 'START_ROUND') {
      return;
    }
    return playerIds.map((playerId) => {

      const nonDeclaredCards = playerHands[playerId]
      // If not playing tricks, declared cards should be shown in front,
      // not in hand
          .filter((cardId) => status === 'PLAY' ||
          declaredCards.length === 0 ||
          declaredCards[declaredCards.length - 1].
              cardIds.every((declaredCardId) => cardId !== declaredCardId));

      return (
        <PlayerArea
          key={`playerArea${playerId}`}
          myPlayerId={myPlayerId}
          playerIds={playerIds}
          playerId={playerId}
          distance={0.6}
        >
          <Cards
            cardIds={nonDeclaredCards}
            selectedCardIds={selectedCardIds}
            cardsById={cardsById}
            faceUp={playerId === myPlayerId}
            selectCards={playerId === myPlayerId ? cardId => this.setState({
              selectedCardIds: {
                ...selectedCardIds,
                [cardId]: !selectedCardIds[cardId],
              },
            }) : undefined}
          />
        </PlayerArea>
      );
    });
  }

  renderDeclaredCards() {
    const {myPlayerId, playerIds, status, cardsById, declaredCards} = this.state;
    if (status === 'START_ROUND' ||
      status === 'PLAY' ||
      declaredCards.length === 0) {
      return;
    }
    const latestDeclaredCards = declaredCards[declaredCards.length - 1];
    return <div>
      <PlayerArea
        myPlayerId={myPlayerId}
        playerIds={playerIds}
        playerId={latestDeclaredCards.playerId}
        distance={0.3}
      >
        <Cards
          cardIds={latestDeclaredCards.cardIds}
          cardsById={cardsById}
          faceUp={true}
        />
      </PlayerArea>
    </div>;
  }

  renderBottomCards() {
    const { myPlayerId, playerIds, starterPlayerIndex, status, cardsById, exposedBottomCards } = this.state;
    if (status !== 'EXPOSE_BOTTOM_CARDS') {
      return;
    }
    return <div>
      <PlayerArea
        myPlayerId={myPlayerId}
        playerIds={playerIds}
        playerId={playerIds[starterPlayerIndex]}
        distance={0.3}
      >
        <Cards
          cardIds={exposedBottomCards}
          cardsById={cardsById}
          faceUp={true}
        />
      </PlayerArea>
    </div>;
  }

  renderCurrentTrick() {
    const {myPlayerId, showPreviousTrick, playerIds, status, cardsById, pastTricks, currentTrick} = this.state;
    if (!currentTrick) {
      return;
    }
    if (showPreviousTrick && pastTricks.length > 0) {
      return <Trick
        trick={pastTricks[pastTricks.length - 1]}
        myPlayerId={myPlayerId}
        playerIds={playerIds}
        cardsById={cardsById}
      />
    }
    if (status === 'START_ROUND') {
      return;
    }
    return <Trick
      trick={currentTrick}
      myPlayerId={myPlayerId}
      playerIds={playerIds}
      cardsById={cardsById}
    />
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
    const selectedCardIdsList = Object.entries(selectedCardIds)
        .filter(([_cardId, selected]) => selected)
        .map(([cardId, _selected]) => cardId);
    const iAmReadyForPlay = playerReadyForPlay[myPlayerId];
    const numPlayersReadyForPlay = Object.values(playerReadyForPlay).filter(ready => ready).length;

    if (status === 'DRAW_KITTY' && selectedCardIdsList.length === 0) {
      return <ActionButton
        text={`Ready (${numPlayersReadyForPlay}/${playerIds.length})`}
        active={!iAmReadyForPlay}
        onClick={() => this.connection.send({ READY_FOR_PLAY: { ready: !iAmReadyForPlay } })}
      />;
    }

    if ((status === 'DRAW' || status === 'DRAW_KITTY') && !iAmReadyForPlay) {
      return <ActionButton
        text='Declare'
        active={selectedCardIdsList.length > 0}
        onClick={() => {
          const cardIds = [...selectedCardIdsList];

          // if you currently declared cards already, add them as well
          if (declaredCards.length > 0 &&
            declaredCards[declaredCards.length - 1].playerId === myPlayerId) {
            cardIds.push(...declaredCards[declaredCards.length - 1].cardIds);
          }

          this.connection.send({ DECLARE: { cardIds } });
          this.setState({selectedCardIds: {}});
        }}
      />;
    }

    if (playerIds[currentPlayerIndex] !== myPlayerId) {
      return;
    }

    if (status === 'MAKE_KITTY' && kitty.length === 0) {
      return <ActionButton
        text='Make kitty'
        active={selectedCardIdsList.length === kittySize}
        onClick={() => {
          this.connection.send({ MAKE_KITTY: { cardIds: selectedCardIdsList } });
          this.setState({selectedCardIds: {}});
        }}
      />;
    }
    if (status === 'PLAY') {
      return <ActionButton
        text='Play'
        active={selectedCardIdsList.length > 0}
        onClick={() => {
          this.connection.send({ PLAY: { cardIds: selectedCardIdsList } });
          this.setState({selectedCardIds: {}});
        }}
      />;
    }
  }

  renderKitty() {
    const { myPlayerId, playerIds, starterPlayerIndex, status, cardsById, kitty } = this.state;

    return <Kitty
      myPlayerId={myPlayerId}
      playerIds={playerIds}
      starterPlayerIndex={starterPlayerIndex}
      status={status}
      cardsById={cardsById}
      kitty={kitty}
    />;
  }

  renderLastTrickButton() {
    const {pastTricks} = this.state;
    if (!pastTricks || pastTricks.length === 0) {
      return;
    }
    return <div
      className='last_trick_button'
      onMouseEnter={() => this.setState({showPreviousTrick: true})}
      onMouseLeave={() => this.setState({showPreviousTrick: false})}
    />;
  }
}

Room.propTypes = {
  roomCode: PropTypes.string,
};
