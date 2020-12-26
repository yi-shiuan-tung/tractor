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
            clicked,
            onClick,
            title,
        } = this.props;

        return <div
            className={classNames('action_button', 'button', onClick ? 'primary' : 'disabled', { 'clicked': clicked })}
            onClick={onClick}
            title={title}
        >
            {text}
        </div>;
    }
}
