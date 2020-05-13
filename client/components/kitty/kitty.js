import * as React from 'react';
import { Cards } from '../cards';
import { HoverButton } from '../hoverButton/hoverButton';
import './kitty.css';

/**
 * Render a "view kitty button" (if you have access to it), and if hovering
 * over it, renders the kitty cards.
 */
export class Kitty extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            showKitty: false,
        };
    }

    render() {
        const {
            myPlayerId,
            playerIds,
            starterPlayerIndex,
            status,
            cardsById,
            kitty,
        } = this.props;

        if (status !== 'START_ROUND' && playerIds[starterPlayerIndex] !== myPlayerId) {
            return null;
        }
        if (!kitty || kitty.length === 0) {
            return null;
        }
        return <div className='kitty'>
            {this.renderKittyCards(cardsById, kitty)}
            {this.renderViewKittyButton()}
        </div>
    }

    renderViewKittyButton() {
        return <HoverButton
            className='view_kitty_button'
            onHoverStart={() => this.setState({ showKitty: true })}
            onHoverEnd={() => this.setState({ showKitty: false })}
        />;
    }

    renderKittyCards(cardsById, kitty) {
        const { showKitty } = this.state;

        if (showKitty) {
            return <Cards
                className='kitty_cards'
                cardIds={kitty}
                cardsById={cardsById}
                faceUp={true}
            />;
        }
    }
}
