import * as classNames from 'classnames';
import * as React from 'react';
import './actionButton.css';

/**
 */
export class ActionButton extends React.Component {

    render() {
        const {
            text,
            active, // boolean representing whether the user is expected to click on it
            onClick,
        } = this.props;

        return <div
            className={classNames('action_button', 'bottom_button', { 'clickable': onClick !== undefined }, { 'active': active })}
            onClick={onClick}
        >
            {text}
        </div>;
    }
}
