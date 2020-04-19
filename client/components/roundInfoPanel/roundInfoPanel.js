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
            currentRoundPenalties,
            currentTrump,
        } = this.props;

        if (!isDeclaringTeam) {
            return null;
        }
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
                <div>Starter: {this.renderPlayerId(playerIds[starterPlayerIndex])}</div>
                <div>Opponent&apos;s points: {opponentsPoints}</div>
                {this.maybeRenderFindAFriendDeclaration(findAFriendDeclaration)}
                {this.maybeRenderPenalties(currentRoundPenalties)}
            </div>
        );
    }

    maybeRenderFindAFriendDeclaration(findAFriendDeclaration) {
        if (!findAFriendDeclaration) {
            return;
        }
        return (
            <div className="section">
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

    maybeRenderPenalties(currentRoundPenalties) {
        if (!currentRoundPenalties) {
            return;
        }
        const playerIdsWithPenalties = Object.keys(currentRoundPenalties)
            .filter(playerId => currentRoundPenalties[playerId] > 0);
        if (playerIdsWithPenalties.length === 0) {
            return;
        }
        return (
            <div className="section">
                <div>Penalties:</div>
                {playerIdsWithPenalties.map((playerId, index) => {
                    return <div key={`penalty${index}`}>
                        {this.renderPlayerId(playerId)}
                        {`: ${currentRoundPenalties[playerId]} points`}
                    </div>;
                })}
            </div>
        );
    }

    renderPlayerId(playerId) {
        const { playerNames, myPlayerId } = this.props;
        return playerId === myPlayerId ? <span className='me'>{'You'}</span> : playerNames[playerId];
    }
}
