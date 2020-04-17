import * as React from 'react';
import { PlayerArea } from '../playerArea';
import './trick.css';

/**
 * Renders one trick (a set of cards from each player, and a crown over the
 * winning player's cards).
 */
export class Trick extends React.Component {

    constructor(props) {
        super(props);
    }

    render() {
        const { trick, playerIds, myId, renderCards } = this.props;
        return (
            <span className="trick">
                {trick.plays.map(({ playerId, cardIds }) => {
                    return <PlayerArea
                        key={`playerArea${playerId}`}
                        playerIds={playerIds}
                        playerId={playerId}
                        myId={myId}
                        distance={0.2}
                    >
                        {renderCards(cardIds, {
                            interCardDistance: 15,
                            faceUp: true,
                            canSelect: false,
                        })}
                        {playerId === trick.winningPlayerId ? <span className="winner" /> : undefined}
                    </PlayerArea>
                })}
            </span>
        );
    }
}
