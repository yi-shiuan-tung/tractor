import * as React from 'react';
import {PlayerArea} from './components/playerArea';

export class Trick extends React.Component {

    constructor(props) {
        super(props);
    }

    render() {
        const { trick, playerIds, myId, renderCards } = this.props;
        return trick.plays.map(({ playerId, cardIds }) => {
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
                {playerId === trick.winningPlayerId ? <span className="big_crown" /> : undefined}
            </PlayerArea>
        });
    }
}
