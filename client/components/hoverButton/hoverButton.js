import * as React from 'react';

/**
 * A button that can be hovered over, or equivalently touched on mobile devices.
 */
export class HoverButton extends React.Component {

    render() {
        const { className, onHoverStart, onHoverEnd } = this.props;
        return <div
            className={className}
            style={{WebkitUserSelect: 'none'}} // Don't show popup after mobile touch
            onMouseEnter={onHoverStart}
            onMouseLeave={onHoverEnd}
            onTouchStart={onHoverStart}
            onTouchEnd={onHoverEnd}
        />
    }
}
