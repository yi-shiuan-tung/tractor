import * as classNames from 'classnames';
import PropTypes from 'prop-types';
import * as React from 'react';
import {getCardImageSrc, getFaceDownCardImageSrc} from '../../lib/cardImages';
import { ORDINALS, SUITS, VALUES } from '../../lib/cards';
import {setUpConnection} from '../../providers/connection';
import {LOCATION} from '../../lib/consts';
import './room.css';
import gameStart from '../../assets/audio/game_start.mp3';
import yourTurn from '../../assets/audio/your_turn.mp3';
import gameOver from '../../assets/audio/game_over.mp3';

import Card from '../../components/Card';
import {PlayerArea} from '../../components/playerArea';
import { Trick } from '../../trick';
import { FindAFriendPanel } from '../../components/findAFriendPanel/findAFriendPanel';


export const WIDTH = 1200;
export const HEIGHT = 800;

export const CARD_WIDTH = 71;
export const CARD_HEIGHT = 96;

export class Game extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      // local state
      isMyNameEditable: false,
      inputMyName: '',
      ais: [], // PlayerId[]
      playerNames: {}, // {playerId: playerName}
      selectedCardIds: {}, // {cardId: boolean}
      notifications: {},
      showPreviousTrick: false,
      showKitty: false,
      playerReadyForPlay: {}, // {playerId: boolean}
      confirmDoesItFlyCards: undefined, // CardId[]?
      soundVolume: 3,

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
    this.audio = new Audio();
    this.subSocket = setUpConnection(
        LOCATION + 'tractor/' + roomCode,
        (response) => this.myId = response.request.uuid,
        (response) => {
          const {playerNames, playerIds, status, cardsById} = this.state;

          const message = response.responseBody;
          console.log('Received message: ' + message);

          const json = JSON.parse(message);

          if (json.WELCOME) {
            const {playerNames} = json.WELCOME;
            this.setState({playerNames});
          } else if (json.UPDATE_PLAYERS) {
            this.setState(json.UPDATE_PLAYERS);
          } else if (json.UPDATE_AIS) {
            this.setState(json.UPDATE_AIS);
          } else if (json.GAME_CONFIGURATION) {
            this.setState(json.GAME_CONFIGURATION);
          } else if (json.START_ROUND) {
            this.setState(json.START_ROUND);
            this.playAudio(gameStart);
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
              this.playAudio(yourTurn);
            }
          } else if (json.FINISH_TRICK) {
            const {doDeclarersWin, ...other} = json.FINISH_TRICK;
            this.setState(other);
            if (other.status === 'START_ROUND') {
              this.setNotification(
                 doDeclarersWin ? 'Declarers win!' : 'Opponents win!',
              );
              this.playAudio(gameOver);
            } else if (other.status === 'PLAY' && playerIds[other.currentPlayerIndex] === this.myId) {
              this.playAudio(yourTurn);
            }
          } else if (json.CONFIRM_DOES_IT_FLY) {
            const {cardIds} = json.CONFIRM_DOES_IT_FLY;
            this.setState({confirmDoesItFlyCards: cardIds})
          } else if (json.FRIEND_JOINED) {
            const {playerId, ...other} = json.FRIEND_JOINED;
            this.setNotification(`${playerNames[playerId]} has joined the declaring team!`);
            this.setState(other);
          } else if (json.FORFEIT) {
            const {playerId, message, ...other} = json.FORFEIT;
            this.setNotification(`${playerNames[playerId]} ${message}.`);
            this.setState(other);
          } else if (json.INVALID_ACTION) {
            this.setNotification(json.INVALID_ACTION.message);
          } else {
            console.log('Unhandled message: ' + JSON.stringify(json));
          }
        });
  }

  componentWillUnmount() {
    this.subSocket.disconnect();
  }

  playAudio(audio) {
    const { soundVolume } = this.state;
    if (soundVolume > 0) {
      this.audio.src = audio;
      this.audio.currentTime = 0;
      this.audio.play();
    }
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
          <button type='button' onClick={() => {
            this.subSocket.push(JSON.stringify({'FORFEIT': {}}));
          }}>
            {'Forfeit'}
          </button>
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
      <button type='button' onClick={() => {
        this.subSocket.push(JSON.stringify({ 'ADD_AI': {} }));
      }}>
        {'Add AI (beta)'}
      </button>
    );
  }

  renderGameArea() {
    return (
      <div
        className='game_area'
        style={{width: `${WIDTH}px`, height: `${HEIGHT}px`}}
      >
        {this.renderRoundInfo()}
        {this.renderGameInfo()}
        {this.renderPlayerNames()}
        {this.renderNotifications()}
        {this.maybeRenderFindAFriendPanel()}
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

  renderRoundInfo() {
    const {
      playerNames,
      playerIds,
      declarerPlayerIndex,
      isDeclaringTeam,
      currentRoundScores,
      currentTrump,
    } = this.state;
    if (currentTrump === undefined) {
      return;
    }
    const declarer = playerIds[declarerPlayerIndex] === this.myId ?
        <span className='me'>{'You'}</span>:
        playerNames[playerIds[declarerPlayerIndex]];
    const trumpSuit = currentTrump.suit === 'JOKER' ?
        'NO TRUMP' : currentTrump.suit + 'S';
    let opponentsPoints = 0;
    playerIds.forEach((playerId) => {
      if (!isDeclaringTeam[playerId]) {
        opponentsPoints += currentRoundScores[playerId];
      }
    });
    return (
      <div className='round_info'>
        <div>Current trump: {VALUES[currentTrump.value]} of {trumpSuit}</div>
        <div>Declarer: {declarer}</div>
        <div>Opponent&apos;s points: {opponentsPoints}</div>
        {this.maybeRenderFindAFriendDeclaration()}
      </div>
    );
  }

  maybeRenderFindAFriendDeclaration() {
    const { findAFriendDeclaration } = this.state;
    if (!findAFriendDeclaration) {
      return;
    }
    return (
      <div className="friend_declaration">
        <div>Friends:</div>
        {findAFriendDeclaration.declarations.map((declaration, index) => {
          return <div key={`declaration${index}`}>
            {`${ORDINALS[declaration.ordinal]} ${VALUES[declaration.value]} of ${SUITS[declaration.suit]}`}
          </div>;
        })}
      </div>
    );
  }

  renderGameInfo() {
    const {playerNames, playerIds, playerRankScores, status} = this.state;
    if (status === 'START_ROUND') {
      return;
    }
    return (
      <div className='game_info'>
        <div>Player scores:</div>
        <ul>
          {playerIds.map((playerId) => {
            const name = playerId === this.myId ?
                <span className='me'>{'You'}</span> : playerNames[playerId];
            return <li
              key={playerId}
            >
              {name}{`: ${VALUES[playerRankScores[playerId]]}`}
            </li>;
          })}
        </ul>
      </div>
    );
  }

  renderPlayerNames() {
    const {
      isMyNameEditable,
      inputMyName,
      ais,
      playerNames,
      playerReadyForPlay,
      playerIds,
      numDecks,
      findAFriend,
      playerRankScores,
      winningPlayerIds,
      status,
      currentPlayerIndex,
      isDeclaringTeam,
      currentRoundScores,
    } = this.state;
    if (status === 'START_ROUND') {
      const iAmReadyForPlay = playerReadyForPlay[this.myId];
      return (
        <div className='player_list'>
          <div className='title'>Tractor</div>
          <ul>
            {playerIds.map((playerId) => {
              const children = [];
              if (playerId === this.myId) {
                if (playerIds.indexOf(this.myId) !== 0) {
                  children.push(<span
                    className='arrow up'
                    onClick={() => {
                      const index = playerIds.indexOf(this.myId);
                      [playerIds[index], playerIds[index - 1]] =
                        [playerIds[index - 1], playerIds[index]];
                      this.subSocket.push(
                        JSON.stringify({ 'PLAYER_ORDER': { playerIds } }),
                      );
                    }} />);
                }
                if (playerIds.indexOf(this.myId) !== playerIds.length - 1) {
                  children.push(<span
                    className='arrow down'
                    onClick={() => {
                      const index = playerIds.indexOf(this.myId);
                      [playerIds[index], playerIds[index + 1]] =
                        [playerIds[index + 1], playerIds[index]];
                      this.subSocket.push(
                        JSON.stringify({ 'PLAYER_ORDER': { playerIds } }),
                      );
                    }} />);
                }
              }
              if (playerId === this.myId && isMyNameEditable) {
                const setName = () => {
                  this.setState({ isMyNameEditable: false });
                  this.subSocket.push(
                    JSON.stringify({
                      'SET_NAME': { 'name': inputMyName.slice(0, 20) },
                    }),
                  );
                }
                children.push(<input
                  ref={e => e && e.focus()}
                  type='text'
                  value={inputMyName}
                  onChange={e => this.setState({ inputMyName: e.target.value })}
                  onKeyDown={e => e.which === 13 /* enter key */ && setName()}
                  onBlur={setName}
                />);
              } else {
                children.push(playerNames[playerId]);
              }
              if (playerId === this.myId && !isMyNameEditable) {
                children.push(<a
                  className='edit_name'
                  onClick={() => {
                    this.setState({ isMyNameEditable: true, inputMyName: playerNames[this.myId] });
                  }}
                />);
              }
              children.push(` (rank ${VALUES[playerRankScores[playerId]]})`);
              if (playerId === this.myId) {
                children.push(<span className='me'> (YOU)</span>);
              }
              if (ais.indexOf(playerId) >= 0) {
                children.push(<span> (AI)</span>);
              }
              if (winningPlayerIds.indexOf(playerId) >= 0) {
                children.push(<span className='crown' />);
              }
              return <li key={playerId}>{children}</li>;
            })}
          </ul>
          <div className='game_properties'>
            <div>
              <i
                className={numDecks < 10 ? 'arrow up' : 'hidden'}
                onClick={() =>
                  this.subSocket.push(
                      JSON.stringify({'GAME_CONFIGURATION': {numDecks: numDecks + 1, findAFriend}}),
                  )}
              />
              <i
                className={numDecks > 1 ? 'arrow down' : 'hidden'}
                onClick={() =>
                  this.subSocket.push(
                      JSON.stringify({'GAME_CONFIGURATION': {numDecks: numDecks - 1, findAFriend}}),
                  )}
              />
              {`${numDecks} ${numDecks > 1 ? 'decks' : 'deck'}`}
            </div>
            <div className={playerIds.length >= 4 ? '' : 'hidden'}>
              <input
                type="checkbox"
                checked={findAFriend}
                onChange={() =>
                  this.subSocket.push(
                      JSON.stringify({'GAME_CONFIGURATION': {numDecks, findAFriend: !findAFriend}}),
                  )}
              />
              {"Find a friend mode"}
            </div>
          </div>
          <div
            className={iAmReadyForPlay ?
              'button primary start_game_button' :
              'inactive button primary start_game_button'}
            onClick={() => {
              this.subSocket.push(JSON.stringify({ 'READY_FOR_PLAY': { ready: !iAmReadyForPlay } }));
            }}
          >
            {`Start round ${this.getNumPlayersReadyString()}`}
          </div>
        </div>
      );
    } else {
      return <div>
        {playerIds
            .map((playerId) => {
              let className = 'player_name';
              if (status !== 'DRAW' &&
                playerId === playerIds[currentPlayerIndex]) {
                className += ' current';
              }
              let playerInfo = undefined;
              if (isDeclaringTeam[playerId]) {
                playerInfo = 'DECL. TEAM';
              } else if (findAFriend) {
                const numPoints = currentRoundScores[playerId];
                if (numPoints > 0) {
                  playerInfo = `${numPoints} pts.`
                }
              }
              return <PlayerArea
                key={`playerArea${playerId}`}
                playerIds={playerIds}
                playerId={playerId}
                myId={this.myId}
                distance={0.91}
                shiftX={playerId === this.myId ? 204 : 0}
                isText={true}
              >
                <div>
                  <span className={className}>{playerNames[playerId]}</span>
                  {playerInfo ? <span className='player_info'>{playerInfo}</span> : undefined}
                </div>
              </PlayerArea>;
            })}
      </div>;
    }
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
      return (
        <div className='confirm_does_it_fly'>
          {'That is a special play. If it doesn\'t fly, you will forfeit the round.'}
          <button
            onClick={() => {
              this.subSocket.push(JSON.stringify({ 'PLAY': { cardIds: confirmDoesItFlyCards, confirmDoesItFly: true } }));
              this.setState({ confirmDoesItFlyCards: undefined });
            }}
          >
            {'Confirm'}
          </button>
          <button
            onClick={() => this.setState({ confirmDoesItFlyCards: undefined })}
          >
            {'Cancel'}
          </button>
        </div>
      );
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

  maybeRenderFindAFriendPanel() {
    const { playerIds, findAFriend, declarerPlayerIndex, status, findAFriendDeclaration } = this.state;
    if (findAFriend && status === 'MAKE_KITTY' && playerIds[declarerPlayerIndex] === this.myId && !findAFriendDeclaration) {
      return (
        <FindAFriendPanel
          numFriends={Math.floor(playerIds.length / 2 - 1)}
          setFindAFriendDeclaration={declarations => this.subSocket.push(JSON.stringify({ 'FRIEND_DECLARE': { declaration: { declarations } } }))}
        />
      );
    }
  }

  renderPlayerHands() {
    const {status, playerIds, playerHands, declaredCards} = this.state;
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
    const {playerIds, status, declaredCards} = this.state;
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
        {this.renderCards(latestDeclaredCards.cardIds, {
          interCardDistance: 15,
          faceUp: true,
          canSelect: false,
        })}
      </PlayerArea>
    </div>;
  }

  renderCurrentTrick() {
    const {showPreviousTrick, playerIds, status, pastTricks, currentTrick} = this.state;
    if (!currentTrick) {
      return;
    }
    if (showPreviousTrick && pastTricks.length > 0) {
      return <Trick
        trick={pastTricks[pastTricks.length - 1]}
        playerIds={playerIds}
        myId={this.myId}
        renderCards={this.renderCards}
      />
    }
    if (status === 'START_ROUND') {
      return;
    }
    return <Trick
      trick={currentTrick}
      playerIds={playerIds}
      myId={this.myId}
      renderCards={this.renderCards}
    />
  }

  renderSettings() {
    const { soundVolume } = this.state;
    return (
      <div
        className={classNames('settings', `sound${soundVolume}`)}
        onClick={() => {
          const newSoundVolume = (soundVolume + 1) % 4;
          this.audio.volume = newSoundVolume / 3;
          this.setState({soundVolume: newSoundVolume});
        }}
      />
    );
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

    if (status === 'DRAW_KITTY' && selectedCardIdsList.length === 0) {
      return <div
        className={classNames('action_button', 'bottom_button', 'clickable', {'active': iAmReadyForPlay})}
        onClick={() => {
          this.subSocket.push(JSON.stringify({'READY_FOR_PLAY': {ready: !iAmReadyForPlay}}));
        }}
      >
        {`Ready ${this.getNumPlayersReadyString()}`}
      </div>;
    }

    if ((status === 'DRAW' || status === 'DRAW_KITTY') && !iAmReadyForPlay) {
      return <div
        className={classNames('action_button', 'bottom_button', {'clickable active': selectedCardIdsList.length > 0})}
        onClick={() => {
          const cardIds = [...selectedCardIdsList];
          if (declaredCards.length > 0 &&
            declaredCards[declaredCards.length - 1].playerId === this.myId) {
            cardIds.push(...declaredCards[declaredCards.length - 1].cardIds);
          }
          this.subSocket.push(JSON.stringify({'DECLARE': {cardIds}}));
          this.setState({selectedCardIds: {}});
        }}
      >
        {'Declare'}
      </div>;
    }

    if (playerIds[currentPlayerIndex] !== this.myId) {
      return;
    }

    if (status === 'MAKE_KITTY' && kitty.length === 0) {
      return <div
        className={classNames('action_button', 'bottom_button', {'clickable active': selectedCardIdsList.length === kittySize})}
        onClick={() => {
          this.subSocket.push(
              JSON.stringify({'MAKE_KITTY': {cardIds: selectedCardIdsList}}),
          );
          this.setState({selectedCardIds: {}});
        }}
      >
        {'Make kitty'}
      </div>;
    }
    if (status === 'PLAY') {
      return <div
        className={classNames('action_button', 'bottom_button', {'clickable active': selectedCardIdsList.length > 0})}
        onClick={() => {
          this.subSocket.push(
              JSON.stringify({'PLAY': {cardIds: selectedCardIdsList}}),
          );
          this.setState({selectedCardIds: {}});
        }}
      >
        {'Play'}
      </div>;
    }
  }

  renderKitty() {
    const {status, kitty, playerIds, declarerPlayerIndex} = this.state;

    if ((status === 'START_ROUND' || playerIds[declarerPlayerIndex] === this.myId) && kitty && kitty.length !== 0) {
      return <div>
        {this.renderKittyCards()}
        {this.renderViewKittyButton()}
      </div>
    }
  }

  renderViewKittyButton() {
     return <div
        className='view_kitty_button'
        onMouseEnter={() => this.setState({showKitty: true})}
        onMouseLeave={() => this.setState({showKitty: false})}
     />
  }

  renderKittyCards() {
    const {kitty, showKitty} = this.state;

    if (showKitty) {
      return <div className='kitty'>
        {this.renderCards(kitty, {
          interCardDistance: 15,
          faceUp: true,
          canSelect: false,
        })}
      </div>
    }
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

  renderCards = (cardIds, args) => {
    const {interCardDistance, faceUp, canSelect} = args;
    const {selectedCardIds, cardsById} = this.state;

    const totalWidth = CARD_WIDTH + interCardDistance * (cardIds.length - 1);
    const cardImgs = cardIds
        .map((cardId, index) => {
          const x = -totalWidth / 2 + interCardDistance * index;
          const y = selectedCardIds[cardId] ? -20 : 0;
          const src = faceUp ?
            getCardImageSrc(cardsById[cardId]) : getFaceDownCardImageSrc();
          const onClick = canSelect ? () => this.setState({
            selectedCardIds: {
              ...selectedCardIds,
              [cardId]: !selectedCardIds[cardId],
            },
          }) : undefined;
          const input = {
            x, y, src, onClick,
          };
          return <Card key={cardId} {...input} />;
        });
    return <div>{cardImgs}</div>;
  }

  getNumPlayersReadyString() {
    const { playerReadyForPlay, playerIds } = this.state;
    const numPlayersReadyForPlay = Object.values(playerReadyForPlay).filter(ready => ready).length;
    return `(${numPlayersReadyForPlay}/${playerIds.length})`;
  }
}

Game.propTypes = {
  roomCode: PropTypes.string,
};
