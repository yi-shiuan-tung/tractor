import * as classNames from 'classnames';
import * as React from 'react';
import './playerNametag.css';

/**
 * The "nametag" for a single player. Shown in the game area near the player's
 * location, and contains the player's name and other relevant info.
 */
export class PlayerNametag extends React.Component {

    render() {
        const {
            playerId,
            playerNames,
            playerIds,
            findAFriend,
            status,
            currentPlayerIndex,
            isDeclaringTeam,
            currentRoundScores,
        } = this.props;

        let playerInfo = undefined;
        if (isDeclaringTeam[playerId]) {
            playerInfo = 'DECL. TEAM';
        } else if (findAFriend) {
            const numPoints = currentRoundScores[playerId];
            if (numPoints > 0) {
                playerInfo = `${numPoints} pts.`
            }
        }
        return <div className='player_nametag'>
            <span className={classNames('name', { 'current': status !== 'DRAW' && playerIds[currentPlayerIndex] === playerId })}>
                {playerNames[playerId]}
            </span>
            {playerInfo ? <span className='player_info'>{playerInfo}</span> : undefined}
        </div>;
    }
}
