import PropTypes from 'prop-types';
import * as React from 'react';
import { getAudio } from '../../lib/audio';
import {setUpConnection} from '../../lib/connection';
import {LOCATION} from '../../lib/consts';
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
  RoundInfoPanel,
  RoundStartPanel,
  SettingsPanel,
  Trick,
} from '../../components';

export class Game extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      // local state
      ais: [], // PlayerId[]
      playerNames: {}, // {playerId: playerName}
      selectedCardIds: {}, // {cardId: boolean}
      notifications: {},
      showPreviousTrick: false,
      playerReadyForPlay: {}, // {playerId: boolean}
      confirmDoesItFlyCards: undefined, // CardId[]?
      soundVolume: 3, // 0, 1, 2, or 3

      // game state
      playerIds: [], // PlayerId[]
      numDecks: 2, // integer
      findAFriend: false, // boolean
      kittySize: 8, // integer
      roundNumber: undefined, // integer
      declarerPlayerIndex: undefined, // integer
      playerRankScores: {}, // {playerId: cardValue}
      winningPlayerIds: [], // PlayerId[]
      status: 'START_ROUND', // GameStatus
      currentPlayerIndex: undefined, // integer
      isDeclaringTeam: undefined, // {playerId: boolean}
      deck: undefined, // cardId[]
      cardsById: undefined, // {cardId: Card}
      playerHands: undefined, // {playerId: cardId[]}
      declaredCards: undefined, // Play[]
      kitty: undefined, // Card[]
      findAFriendDeclaration: undefined, // FindAFriendDeclaration
      pastTricks: undefined, // Trick[]
      currentTrick: undefined, // Trick
      currentRoundScores: undefined, // {playerId: integer}
      currentTrump: undefined, // Card
    };
  }

  componentDidMount() {
    const {roomCode} = this.props;
    this.audio = getAudio();
    this.connection = setUpConnection(
        LOCATION + 'tractor/' + roomCode,
        myId => this.myId = myId,
        json => {
          const {playerNames, playerIds, status, cardsById} = this.state;

          if (json.ROOM_STATE) {
            this.setState(json.ROOM_STATE);
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
          } else if (json.TAKE_KITTY) {
            this.setState(json.TAKE_KITTY);
          } else if (json.FRIEND_DECLARE) {
            this.setState(json.FRIEND_DECLARE);
          } else if (json.MAKE_KITTY) {
            this.setState(json.MAKE_KITTY);
          } else if (json.PLAY) {
            this.setState(json.PLAY);
            if (status === 'PLAY' && playerIds[json.PLAY.currentPlayerIndex] === this.myId) {
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
            } else if (other.status === 'PLAY' && playerIds[other.currentPlayerIndex] === this.myId) {
              this.audio.playYourTurn();
            }
          } else if (json.CONFIRM_DOES_IT_FLY) {
            const {cardIds} = json.CONFIRM_DOES_IT_FLY;
            this.setState({confirmDoesItFlyCards: cardIds})
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
          } else if (json.INVALID_ACTION) {
            this.setNotification(json.INVALID_ACTION.message);
          } else {
            console.error('Unhandled message: ' + JSON.stringify(json));
          }
        });
  }

  componentWillUnmount() {
    this.connection.disconnect();
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
          {this.maybeRenderAddAiButton()}
        </div>
        {this.renderGameArea()}
      </div>
    );
  }

  maybeRenderAddAiButton() {
    const { status } = this.state;
    if (status !== 'START_ROUND') {
      return;
    }
    return (
      <button type='button' onClick={() => this.connection.send({ ADD_AI: {} })}>
        {'Add AI (beta)'}
      </button>
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
      ais,
      playerNames,
      playerReadyForPlay,
      playerIds,
      numDecks,
      findAFriend,
      playerRankScores,
      winningPlayerIds,
      status,
    } = this.state;
    if (status === 'START_ROUND') {
      return <RoundStartPanel
        ais={ais}
        playerNames={playerNames}
        playerReadyForPlay={playerReadyForPlay}
        playerIds={playerIds}
        myId={this.myId}
        numDecks={numDecks}
        findAFriend={findAFriend}
        playerRankScores={playerRankScores}
        winningPlayerIds={winningPlayerIds}
        setPlayerOrder={playerIds => this.connection.send({ PLAYER_ORDER: { playerIds }})}
        setPlayerScore={(playerId, increment) => this.connection.send({ PLAYER_SCORE: { playerId, increment }})}
        setName={name => this.connection.send({ SET_NAME: { name }})}
        setGameConfiguration={gameConfiguration => this.connection.send({ GAME_CONFIGURATION: gameConfiguration })}
        setReadyForPlay={ready => this.connection.send({ READY_FOR_PLAY: { ready }})}
      />;
    }
  }

  renderRoundInfo() {
    const {
      playerNames,
      playerIds,
      declarerPlayerIndex,
      isDeclaringTeam,
      findAFriendDeclaration,
      currentRoundScores,
      currentTrump,
    } = this.state;
    return <RoundInfoPanel
      playerNames={playerNames}
      playerIds={playerIds}
      declarerPlayerIndex={declarerPlayerIndex}
      isDeclaringTeam={isDeclaringTeam}
      findAFriendDeclaration={findAFriendDeclaration}
      currentRoundScores={currentRoundScores}
      currentTrump={currentTrump}
      myId={this.myId}
    />;
  }

  renderGameInfo() {
    const {playerNames, playerIds, playerRankScores, status} = this.state;
    if (status === 'START_ROUND') {
      return; // all info is already shown in the round start panel
    }
    return <GameInfoPanel
      playerNames={playerNames}
      playerIds={playerIds}
      playerRankScores={playerRankScores}
      status={status}
      myId={this.myId}
    />;
  }

  renderPlayerNames() {
    const {
      playerNames,
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
        playerIds={playerIds}
        playerId={playerId}
        myId={this.myId}
        distance={0.91}
        shiftX={playerId === this.myId ? 204 : 0}
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
      playerNames,
      notifications,
      playerReadyForPlay,
      confirmDoesItFlyCards,
      playerIds,
      kittySize,
      status,
      currentPlayerIndex,
    } = this.state;
    if (confirmDoesItFlyCards !== undefined) {
      return <ConfirmationPanel
        message={'That is a special play. If it doesn\'t fly, you will forfeit the round.'}
        confirm={() => {
          this.connection.send({ PLAY: { cardIds: confirmDoesItFlyCards, confirmDoesItFly: true } });
          this.setState({ confirmDoesItFlyCards: undefined });
        }}
        cancel={() => this.setState({ confirmDoesItFlyCards: undefined })}
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
    if (!playerReadyForPlay[this.myId] && status === 'DRAW_KITTY') {
      return <div className='notification'>{"Select card(s) to declare, or press Ready"}</div>
    }
    const playerId = playerIds[currentPlayerIndex];
    if (status === 'MAKE_KITTY') {
      if (playerId === this.myId) {
        return <div className='notification'>{`Select ${kittySize} cards to put in the kitty`}</div>
      } else {
        return <div className='notification'>{`${playerNames[playerId]} is selecting cards for the kitty`}</div>
      }
    }
    if (status === 'PLAY' && playerId === this.myId) {
      return <div className='notification short'>{'Your turn'}</div>
    }
  }

  renderFindAFriendPanel() {
    const { playerIds, findAFriend, declarerPlayerIndex, status, findAFriendDeclaration } = this.state;
    if (findAFriend && status === 'MAKE_KITTY' && playerIds[declarerPlayerIndex] === this.myId && !findAFriendDeclaration) {
      return (
        <FindAFriendPanel
          playerIds={playerIds}
          setFindAFriendDeclaration={declarations => this.connection.send({ FRIEND_DECLARE: { declaration: { declarations } } })}
        />
      );
    }
  }

  renderPlayerHands() {
    const {selectedCardIds, status, playerIds, cardsById, playerHands, declaredCards} = this.state;
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
          playerIds={playerIds}
          playerId={playerId}
          myId={this.myId}
          distance={0.6}
        >
          <Cards
            cardIds={nonDeclaredCards}
            selectedCardIds={selectedCardIds}
            cardsById={cardsById}
            faceUp={playerId === this.myId}
            selectCards={playerId === this.myId ? cardId => this.setState({
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
    const {playerIds, status, cardsById, declaredCards} = this.state;
    if (status === 'START_ROUND' ||
      status === 'PLAY' ||
      declaredCards.length === 0) {
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
        <Cards
          cardIds={latestDeclaredCards.cardIds}
          cardsById={cardsById}
          faceUp={true}
        />
      </PlayerArea>
    </div>;
  }

  renderCurrentTrick() {
    const {showPreviousTrick, playerIds, status, cardsById, pastTricks, currentTrick} = this.state;
    if (!currentTrick) {
      return;
    }
    if (showPreviousTrick && pastTricks.length > 0) {
      return <Trick
        trick={pastTricks[pastTricks.length - 1]}
        playerIds={playerIds}
        cardsById={cardsById}
        myId={this.myId}
      />
    }
    if (status === 'START_ROUND') {
      return;
    }
    return <Trick
      trick={currentTrick}
      playerIds={playerIds}
      cardsById={cardsById}
      myId={this.myId}
    />
  }

  renderSettings() {
    const { leaveRoom } = this.props;
    const { soundVolume, status, currentTrick } = this.state;
    return <SettingsPanel
      soundVolume={soundVolume}
      status={status}
      currentTrick={currentTrick}
      myId={this.myId}
      setSoundVolume={soundVolume => {
        this.audio.setVolume(soundVolume);
        this.setState({ soundVolume });
      }}
      forfeit={() => this.connection.send({ FORFEIT: {} })}
      leaveRoom={leaveRoom}
      takeBack={() => this.connection.send({ TAKE_BACK: {} })}
    />;
  }

  renderActionButton() {
    const {
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
    const iAmReadyForPlay = playerReadyForPlay[this.myId];
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
            declaredCards[declaredCards.length - 1].playerId === this.myId) {
            cardIds.push(...declaredCards[declaredCards.length - 1].cardIds);
          }

          this.connection.send({ DECLARE: { cardIds } });
          this.setState({selectedCardIds: {}});
        }}
      />;
    }

    if (playerIds[currentPlayerIndex] !== this.myId) {
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
    const { playerIds, declarerPlayerIndex, status, cardsById, kitty } = this.state;

    return <Kitty
      playerIds={playerIds}
      declarerPlayerIndex={declarerPlayerIndex}
      status={status}
      cardsById={cardsById}
      kitty={kitty}
      myId={this.myId}
    />;
  }

  renderLastTrickButton() {
    const {pastTricks} = this.state;
    if (pastTricks === undefined || pastTricks.length === 0) {
      return;
    }
    return <div
      className='last_trick_button'
      onMouseEnter={() => this.setState({showPreviousTrick: true})}
      onMouseLeave={() => this.setState({showPreviousTrick: false})}
    />;
  }
}

Game.propTypes = {
  roomCode: PropTypes.string,
};
