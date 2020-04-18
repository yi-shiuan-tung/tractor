import * as React from 'react';
import { ConfirmationPanel } from '../confirmationPanel';
import './settingsPanel.css';

/**
 * Contains per-player game settings.
 */
export class SettingsPanel extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            isConfirmingForfeit: false,
        }
    }

    render() {
        const {
            soundVolume,
            currentTrick,
            myId,
            setSoundVolume, // soundVolume => void
            forfeit, // () => void
            takeBack, // () => void
        } = this.props;
        return (
            <div className='settings_panel'>
                <div
                    className={`button sound sound${soundVolume}`}
                    onClick={() => setSoundVolume((soundVolume + 1) % 4)}
                />
                <div
                    className={"button forfeit"}
                    onClick={() => this.setState({ isConfirmingForfeit: true })}
                />
                {this.maybeRenderTakeBackButton(currentTrick, myId, takeBack)}
                {this.maybeRenderConfirmForfeit(forfeit)}
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

    maybeRenderConfirmForfeit(forfeit) {
        const { isConfirmingForfeit } = this.state;
        if (isConfirmingForfeit) {
            return <ConfirmationPanel
                message='Are you sure you want to forfeit?'
                confirm={() => {
                    forfeit();
                    this.setState({ isConfirmingForfeit: false });
                }}
                cancel={() => this.setState({ isConfirmingForfeit: false })}
            />
        }
    }
}
