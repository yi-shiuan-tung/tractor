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
            isConfirmingLeave: false,
        }
    }

    render() {
        const {
            myPlayerId,
            soundVolume,
            playerIds,
            status,
            currentTrick,
            forfeit, // () => void
            leaveRoom, // () => void
            setSoundVolume, // soundVolume => void
            toggleEditPlayers, // () => void
            takeBack, // () => void
        } = this.props;
        return (
            <div className='settings_panel'>
                {this.maybeRenderConfirm(forfeit, leaveRoom)}
                {this.renderLeaveRoomOrForfeitButton(myPlayerId, playerIds, status)}
                <div
                    className={`button sound sound${soundVolume}`}
                    onClick={() => setSoundVolume((soundVolume + 1) % 4)}
                    title={"Sound volume"}
                />
                {this.maybeRenderEditPlayersButton(status, toggleEditPlayers)}
                {this.maybeRenderTakeBackButton(currentTrick, myPlayerId, takeBack)}
            </div>
        );
    }

    maybeRenderConfirm(forfeit, leaveRoom) {
        const { isConfirmingForfeit, isConfirmingLeave } = this.state;
        if (isConfirmingForfeit) {
            return <ConfirmationPanel
                message='Are you sure you want to forfeit?'
                confirm={() => {
                    forfeit();
                    this.setState({ isConfirmingForfeit: false });
                }}
                cancel={() => this.setState({ isConfirmingForfeit: false })}
            />
        } else if (isConfirmingLeave) {
            return <ConfirmationPanel
                message='Are you sure you want to leave?'
                confirm={() => {
                    leaveRoom();
                    this.setState({ isConfirmingLeave: false });
                }}
                cancel={() => this.setState({ isConfirmingLeave: false })}
            />
        }
    }

    renderLeaveRoomOrForfeitButton(myPlayerId, playerIds, status) {
        const { isConfirmingForfeit, isConfirmingLeave } = this.state;
        if (status === 'START_ROUND' || playerIds.indexOf(myPlayerId) === -1) {
            return <div
                className='button leave_room'
                onClick={() => this.setState({ isConfirmingLeave: !isConfirmingLeave })}
                title={"Leave"}
            />;
        } else {
            return <div
                className='button forfeit'
                onClick={() => this.setState({ isConfirmingForfeit: !isConfirmingForfeit })}
                title={"Forfeit"}
            />;
        }
    }

    maybeRenderEditPlayersButton(status, toggleEditPlayers) {
        if (status !== 'START_ROUND') {
            return;
        }
        return <div
            className='button edit_players'
            onClick={toggleEditPlayers}
            title={"Edit players"}
        />;
    }

    maybeRenderTakeBackButton(currentTrick, myPlayerId, takeBack) {
        if (status === 'START_ROUND' || !currentTrick) {
            return;
        }
        const { plays } = currentTrick;
        if (plays.length > 0 && plays[plays.length - 1].playerId === myPlayerId) {
            return <div
                className='button undo'
                onClick={takeBack}
                title={"Undo"}
            />;
        }
    }
}
