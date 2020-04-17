import * as React from 'react';
import { Cards } from '../cards';
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
        const { trick, playerIds, cardsById, myId } = this.props;
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
                        <Cards
                            cardIds={cardIds}
                            cardsById={cardsById}
                            faceUp={true}
                        />
                        {playerId === trick.winningPlayerId ? <span className="winner" /> : undefined}
                    </PlayerArea>
                })}
            </span>
        );
    }
}
