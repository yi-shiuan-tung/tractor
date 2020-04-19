import * as React from 'react';
import { ORDINALS, SUITS, VALUES } from '../../lib/cards';
import './roundInfoPanel.css';

/**
 * A panel that displays info relevant only to the current round: the current
 * round trump, the current starter, and the current number of card points.
 */
export class RoundInfoPanel extends React.Component {

    render() {
        const {
            playerNames,
            myPlayerId,
            playerIds,
            starterPlayerIndex,
            isDeclaringTeam,
            findAFriendDeclaration,
            currentRoundScores,
            currentTrump,
        } = this.props;

        if (!isDeclaringTeam) {
            return null;
        }
        const starter = playerIds[starterPlayerIndex] === myPlayerId ?
            <span className='me'>{'You'}</span> :
            playerNames[playerIds[starterPlayerIndex]];
        const trumpSuit = currentTrump.suit === 'JOKER' ? 'NO TRUMP' : SUITS[currentTrump.suit];
        let opponentsPoints = 0;
        playerIds.forEach((playerId) => {
            if (!isDeclaringTeam[playerId]) {
                opponentsPoints += currentRoundScores[playerId];
            }
        });
        return (
            <div className='round_info_panel'>
                <div>Current trump: {VALUES[currentTrump.value]} of {trumpSuit}</div>
                <div>Starter: {starter}</div>
                <div>Opponent&apos;s points: {opponentsPoints}</div>
                {this.maybeRenderFindAFriendDeclaration(findAFriendDeclaration)}
            </div>
        );
    }

    maybeRenderFindAFriendDeclaration(findAFriendDeclaration) {
        if (!findAFriendDeclaration) {
            return;
        }
        return (
            <div className="friend_declaration">
                <div>Friends:</div>
                {findAFriendDeclaration.declarations.map((declaration, index) => {
                    return <div key={`declaration${index}`}>
                        {this.renderDeclaration(declaration)}
                    </div>;
                })}
            </div>
        );
    }

    renderDeclaration({ ordinal, value, suit }) {
        if (value === 'BIG_JOKER') {
            return `${ORDINALS[ordinal]} big joker`;
        } else if (value === 'SMALL_JOKER') {
            return `${ORDINALS[ordinal]} small joker`;
        } else {
            return `${ORDINALS[ordinal]} ${VALUES[value]} of ${SUITS[suit]}`;
        }
    }
}
