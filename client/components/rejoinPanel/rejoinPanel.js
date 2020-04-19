import * as React from 'react';
import './rejoinPanel.css';

/**
 * A panel to allow a newly joined player to reconnect as a previous unmapped
 * player (for example, if the player disconnected or accidentally refreshed
 * the page).
 */
export class RejoinPanel extends React.Component {

    render() {
        const {
            aiControllers,
            humanControllers,
            playerNames,
            playerIds,
            rejoin, // playerId => void
        } = this.props;

        const unmappedPlayerIds = playerIds
            .filter(playerId => aiControllers.indexOf(playerId) === -1 && humanControllers.indexOf(playerId) === -1);
        if (unmappedPlayerIds.length === 0) {
            return null;
        }
        return <div className="rejoin_panel">
            <span>Join as previous player?</span>
            <ul>
                {unmappedPlayerIds.map(playerId => {
                    return <li key={playerId}>
                        <button
                            onClick={() => rejoin(playerId)}
                        >
                            {playerNames[playerId]}
                        </button>
                    </li>;
                })}
            </ul>
            <button onClick={() => rejoin(undefined)}>
                {'Join as new player'}
            </button>
        </div>;
    }
}
