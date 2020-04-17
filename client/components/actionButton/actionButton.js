import * as classNames from 'classnames';
import * as React from 'react';
import './actionButton.css';

/**
 * An opinionated button that takes game-related modes as props instead of generic classes.
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
