import * as React from 'react';
import { VALUES } from '../../lib/cards';
import './roundStartPanel.css';

/**
 * The panel shown right before the first round starts, and between rounds.
 *
 * Shows the list of players, and game properties (e.g. number of decks).
 *
 * Allows the players to change the player order, and game properties, then
 * start the game.
 */
export class RoundStartPanel extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            inputMyName: '',
            isMyNameEditable: false,
        }
    }

    render() {
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
            setPlayerOrder, // PlayerId[] => void
            setPlayerScore, // (PlayerId, boolean) => void
            removePlayer, // playerId => void
            setGameConfiguration, // { numDecks, findAFriend } => void
            addAi,
            setReadyForPlay, // boolean => void
        } = this.props;
        const { isMyNameEditable } = this.state;

        if (playerIds.length === 0) {
            return null;
        }
        if (!localName) {
            return (
                <div className='round_start_panel_small'>
                    <span>Enter your name: </span>
                    {this.renderNameInput()}
                </div>
            );
        }

        const iAmReadyForPlay = playerReadyForPlay[myPlayerId];
        const numPlayersReadyForPlay = Object.values(playerReadyForPlay).filter(ready => ready).length;
        const playersNotReadyForPlay = Object.entries(playerReadyForPlay)
            .filter(([_playerId, ready]) => !ready)
            .map(([playerId, _ready]) => playerNames[playerId]);

        return (
            <div className='round_start_panel'>
                <div className='title'>{'Tractor'}</div>
                <ul>
                    {playerIds.map((playerId) => {
                        const children = [];
                        if (isEditingPlayers) {
                            if (playerIds.indexOf(playerId) !== 0) {
                                children.push(<span
                                    key="player_arrow_up"
                                    className='arrow up'
                                    onClick={() => {
                                        const index = playerIds.indexOf(playerId);
                                        [playerIds[index], playerIds[index - 1]] =
                                            [playerIds[index - 1], playerIds[index]];
                                        setPlayerOrder(playerIds);
                                    }} />);
                            }
                            if (playerIds.indexOf(playerId) !== playerIds.length - 1) {
                                children.push(<span
                                    key="player_arrow_down"
                                    className='arrow down'
                                    onClick={() => {
                                        const index = playerIds.indexOf(playerId);
                                        [playerIds[index], playerIds[index + 1]] =
                                            [playerIds[index + 1], playerIds[index]];
                                        setPlayerOrder(playerIds);
                                    }} />);
                            }
                        }
                        if (playerId === myPlayerId && isMyNameEditable) {
                            children.push(this.renderNameInput());
                        } else {
                            children.push(playerNames[playerId]);
                        }
                        if (playerId === myPlayerId && !isMyNameEditable) {
                            children.push(<a
                                key="edit_name"
                                className='edit_name'
                                onClick={() => {
                                    this.setState({ isMyNameEditable: true, inputMyName: playerNames[myPlayerId] });
                                }}
                            />);
                        }

                        children.push(` (rank ${VALUES[playerRankScores[playerId]]})`);
                        if (isEditingPlayers) {
                            children.push(<span key='spacing' className='spacing' />);
                            if (playerRankScores[playerId] !== 'ACE') {
                                children.push(<span
                                    key='score_arrow_up'
                                    className='arrow up'
                                    onClick={() => setPlayerScore(playerId, true)}
                                />);
                            }
                            if (playerRankScores[playerId] !== 'TWO') {
                                children.push(<span
                                    key='score_arrow_down'
                                    className='arrow down'
                                    onClick={() => setPlayerScore(playerId, false)}
                                />);
                            }
                        }

                        if (playerId === myPlayerId) {
                            children.push(<span key="me" className='me'> (YOU)</span>);
                        }
                        if (aiControllers.indexOf(playerId) >= 0) {
                            children.push(<span key={`ai${playerId}`}> (AI)</span>);
                        }
                        if (winningPlayerIds.indexOf(playerId) >= 0) {
                            children.push(<span key={`crown${playerId}`} className='crown' />);
                        }
                        if (humanControllers.indexOf(playerId) === -1) {
                            children.push(<span
                                key={`remove${playerId}`}
                                className='remove'
                                onClick={() => removePlayer(playerId)}
                            />);
                        }
                        return <li key={playerId}>{children}</li>;
                    })}
                    <div className='add_ai_button' onClick={addAi}>{"+ Add AI player"}</div>
                </ul>
                <div className='game_properties'>
                    <div>
                        <i
                            className={numDecks < 10 ? 'arrow up' : 'hidden'}
                            onClick={() => setGameConfiguration({ numDecks: numDecks + 1, findAFriend })}
                        />
                        <i
                            className={numDecks > 1 ? 'arrow down' : 'hidden'}
                            onClick={() => setGameConfiguration({ numDecks: numDecks - 1, findAFriend })}
                        />
                        {`${numDecks} ${numDecks > 1 ? 'decks' : 'deck'}`}
                    </div>
                    <div className={playerIds.length >= 4 ? '' : 'hidden'}>
                        <input
                            type="checkbox"
                            checked={findAFriend}
                            onChange={() => setGameConfiguration({ numDecks, findAFriend: !findAFriend })}
                        />
                        {"Find a friend mode"}
                    </div>
                </div>
                <div
                    className={iAmReadyForPlay ?
                        'button primary clicked start_game_button' :
                        'button primary start_game_button pulse'}
                    onClick={() => setReadyForPlay(!iAmReadyForPlay)}
                    title={`Waiting on ${playersNotReadyForPlay.join(', ')}`}
                >
                    {`${iAmReadyForPlay ? 'Ready' : 'Start round'} (${numPlayersReadyForPlay}/${humanControllers.length})`}
                </div>
            </div>
        );
    }

    renderNameInput() {
        const { setName } = this.props;
        const { inputMyName } = this.state;
        const setNameFunc = () => {
            this.setState({ isMyNameEditable: false });
            setName(inputMyName.slice(0, 20));
        }
        return <input
            ref={e => e && e.focus()}
            key="edit_name_input"
            type='text'
            value={inputMyName}
            onChange={e => this.setState({ inputMyName: e.target.value })}
            onKeyDown={e => e.which === 13 /* enter key */ && setNameFunc()}
            onBlur={setNameFunc}
        />;
    }
}
