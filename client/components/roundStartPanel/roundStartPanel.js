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
            ais,
            playerNames,
            playerReadyForPlay,
            playerIds,
            myId,
            numDecks,
            findAFriend,
            playerRankScores,
            winningPlayerIds,
            setPlayerOrder, // PlayerId[] => void
            setPlayerScore, // (PlayerId, boolean) => void
            setName, // string => void
            setGameConfiguration, // { numDecks, findAFriend } => void
            setReadyForPlay, // boolean => void
        } = this.props;
        const { inputMyName, isMyNameEditable } = this.state;

        const iAmReadyForPlay = playerReadyForPlay[myId];
        const numPlayersReadyForPlay = Object.values(playerReadyForPlay).filter(ready => ready).length;

        return (
            <div className='round_start_panel'>
                <div className='title'>{'Tractor'}</div>
                <ul>
                    {playerIds.map((playerId) => {
                        const children = [];
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
                        if (playerId === myId && isMyNameEditable) {
                            const setNameFunc = () => {
                                this.setState({ isMyNameEditable: false });
                                setName(inputMyName.slice(0, 20));
                            }
                            children.push(<input
                                ref={e => e && e.focus()}
                                key="edit_name_input"
                                type='text'
                                value={inputMyName}
                                onChange={e => this.setState({ inputMyName: e.target.value })}
                                onKeyDown={e => e.which === 13 /* enter key */ && setNameFunc()}
                                onBlur={setNameFunc}
                            />);
                        } else {
                            children.push(playerNames[playerId]);
                        }
                        if (playerId === myId && !isMyNameEditable) {
                            children.push(<a
                                key="edit_name"
                                className='edit_name'
                                onClick={() => {
                                    this.setState({ isMyNameEditable: true, inputMyName: playerNames[myId] });
                                }}
                            />);
                        }

                        children.push(` (rank ${VALUES[playerRankScores[playerId]]})`);
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

                        if (playerId === myId) {
                            children.push(<span key="me" className='me'> (YOU)</span>);
                        }
                        if (ais.indexOf(playerId) >= 0) {
                            children.push(<span key={`ai${playerId}`}> (AI)</span>);
                        }
                        if (winningPlayerIds.indexOf(playerId) >= 0) {
                            children.push(<span key={`crown${playerId}`} className='crown' />);
                        }
                        return <li key={playerId}>{children}</li>;
                    })}
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
                        'button primary start_game_button' :
                        'inactive button primary start_game_button'}
                    onClick={() => setReadyForPlay(!iAmReadyForPlay)}
                >
                    {`Start round (${numPlayersReadyForPlay}/${playerIds.length})`}
                </div>
            </div>
        );
    }
}
