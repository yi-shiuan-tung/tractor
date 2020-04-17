import * as React from 'react';
import './confirmationPanel.css';

export class ConfirmationPanel extends React.Component {

    render() {
        const {
            message, // string
            confirm, // () -> void
            cancel, // () -> void
        } = this.props;
        return (
            <div className='confirmation_panel'>
                {message}
                <button
                    onClick={confirm}
                >
                    {'Confirm'}
                </button>
                <button
                    onClick={cancel}
                >
                    {'Cancel'}
                </button>
            </div>
        );
    }
}
