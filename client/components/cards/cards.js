import * as React from 'react';
import {getCardImageSrc, getFaceDownCardImageSrc} from '../../lib/cardImages';

const CARD_WIDTH = 71;

/**
 * Renders one or more cards.
 */
export class Cards extends React.Component {

    render() {
        const {
            cardIds,
            selectedCardIds,
            cardsById,
            interCardDistance,
            faceUp,
            selectCards,
            ...otherProps
        } = this.props;

        const totalWidth = CARD_WIDTH + interCardDistance * (cardIds.length - 1);
        const cardImgs = cardIds
            .map((cardId, index) => {
                const x = -totalWidth / 2 + interCardDistance * index;
                const y = selectedCardIds && selectedCardIds[cardId] ? -20 : 0;
                const src = faceUp ?
                    getCardImageSrc(cardsById[cardId]) : getFaceDownCardImageSrc();
                const onClick = selectCards ? () => selectCards(cardId) : undefined;
                return (
                    <img
                        key={cardId}
                        style={
                            {
                                position: 'absolute',
                                top: `${y}px`,
                                left: `${x}px`,
                            }
                        }
                        src={src}
                        onClick={onClick}
                    />
                );
            });
        return <div {...otherProps}>{cardImgs}</div>;
    }
}
