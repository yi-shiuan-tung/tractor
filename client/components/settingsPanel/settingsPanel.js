import * as React from 'react';
import './settingsPanel.css';

/**
 * Contains per-player game settings.
 */
export class SettingsPanel extends React.Component {

    render() {
        const {
            soundVolume,
            currentTrick,
            myId,
            setSoundVolume, // soundVolume => void
            takeBack, // () => void
        } = this.props;
        return (
            <div className='settings_panel'>
                <div
                    className={`button sound sound${soundVolume}`}
                    onClick={() => setSoundVolume((soundVolume + 1) % 4)}
                />
                {this.maybeRenderTakeBackButton(currentTrick, myId, takeBack)}
            </div>
        );
    }

    maybeRenderTakeBackButton(currentTrick, myId, takeBack) {
        if (!currentTrick) {
            return;
        }
        const { plays } = currentTrick;
        if (plays.length > 0 && plays[plays.length - 1].playerId === myId) {
            return <div
                className='button undo'
                onClick={takeBack}
            />
        }
    }
}
