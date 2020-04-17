import * as classNames from 'classnames';
import * as React from 'react';
import './settingsPanel.css';

/**
 * Contains per-player game settings.
 */
export class SettingsPanel extends React.Component {

    render() {
        const {
            soundVolume, // 0, 1, 2, or 3
            audio, // Audio
            setSoundVolume, // soundVolume => void
        } = this.props;
        return (
            <div
                className={classNames('settings_panel', `sound${soundVolume}`)}
                onClick={() => {
                    const newSoundVolume = (soundVolume + 1) % 4;
                    audio.volume = newSoundVolume / 3;
                    setSoundVolume(newSoundVolume);
                }}
            />
        );
    }
}
