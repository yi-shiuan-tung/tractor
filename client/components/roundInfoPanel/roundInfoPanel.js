import * as React from 'react';
import { ORDINALS, SUITS, VALUES } from '../../lib/cards';
import './roundInfoPanel.css';

/**
 * A panel that displays info relevant only to the current round: the current
 * round trump, the current declarer, and the current number of card points.
 */
export class RoundInfoPanel extends React.Component {

    render() {
        const {
            playerNames,
            playerIds,
            declarerPlayerIndex,
            isDeclaringTeam,
            findAFriendDeclaration,
            currentRoundScores,
            currentTrump,
            myId,
        } = this.props;

        if (!isDeclaringTeam) {
            return null;
        }
        const declarer = playerIds[declarerPlayerIndex] === myId ?
            <span className='me'>{'You'}</span> :
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
            <div className='round_info_panel'>
                <div>Current trump: {VALUES[currentTrump.value]} of {trumpSuit}</div>
                <div>Declarer: {declarer}</div>
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
                        {`${ORDINALS[declaration.ordinal]} ${VALUES[declaration.value]} of ${SUITS[declaration.suit]}`}
                    </div>;
                })}
            </div>
        );
    }
}
