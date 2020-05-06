import * as classNames from 'classnames';
import PropTypes from 'prop-types';
import * as React from 'react';
import './playerArea.css';
import {getCardImageSrc, getFaceDownCardImageSrc} from "../../lib/cardImages";

// dimensions of game area
export const WIDTH = 1200;
export const HEIGHT = 800;

const CARD_WIDTH = 71;


/*
 * A higher order component that takes the given children and applies a rotation
 * to it so that the children appear in front of a particular player.
 * 
 * Specify a distance from the center, normalized from 0 (in the middle) to 1
 * (very close to the player).
 * 
 * If isText=true, then the text may be rotated an extra 180° so that it faces
 * the player.
 * 
 * The children can also be shifted left or right (from the player's
 * perspective) using shiftX.
 */
export class PlayerArea extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      textWidth: undefined,
    };
  }

  // eslint-disable-next-line camelcase
  UNSAFE_componentWillUpdate(prevProps) {
    if (prevProps.children !== this.props.children) {
      this.setState({textWidth: undefined});
    }
  }

  render() {
    const {
      myPlayerId,
      playerIds,
      playerId,
      distance,
      shiftX = 0,
      isText,
      children,
    } = this.props;
    const {textWidth} = this.state;
    const playerIndex = playerIds.indexOf(playerId);
    const myIndex = playerIds.indexOf(myPlayerId);
    const centerPoint = {
      x: WIDTH * (.5 + Math.sin((playerIndex - myIndex) * 2 *
        Math.PI / playerIds.length) * distance / 2 * 0.9),
      y: HEIGHT * (.5 + Math.cos((playerIndex - myIndex) * 2 *
        Math.PI / playerIds.length) * distance / 2),
    };
    let angle = (myIndex - playerIndex) * 360. / playerIds.length;
    if (isText) {
    // ensure text is always facing the player by possibly rotating 180 degrees
      if (angle < 0) {
        angle += 360;
      }
      if (angle > 90 && angle < 270) {
        angle -= 180;
      }
    }
    let transform = `rotate(${angle}deg)`;
    let ref = undefined;
    if (isText) {
      // In the first loop, render the text anywhere and compute its width from
      // the ref. In subsequent renders, the transform will be correct, so we
      // can remove the ref (and avoid an infinite loop).
      if (textWidth) {
        transform = `translate(-${textWidth / 2}px) ` + transform;
      } else {
        ref = (el) => {
          if (el) {
            this.setState({textWidth: el.clientWidth});
          }
        };
      }
    }
    return (
      <div
        key={playerId}
        className={classNames('player_area', { 'my_area': playerId === myPlayerId })}
        style={{
          top: centerPoint.y,
          left: centerPoint.x + shiftX,
          transform,
        }}
        ref={ref}
      >
        {children}
      </div>
    );
  }
}

PlayerArea.propTypes = {
  myPlayerId: PropTypes.string,
  playerIds: PropTypes.array,
  playerId: PropTypes.string,
  distance: PropTypes.number,
  isText: PropTypes.bool,
  children: PropTypes.any,
};

export function getPlayerPosition(playerIds, playerId, myPlayerId, distance) {
  const numPlayers = playerIds.length;
  const playerIndex = playerIds.indexOf(playerId);
  const myIndex = playerIds.indexOf(myPlayerId);
  let angle = (myIndex - playerIndex) * 360. / numPlayers;
  const centerPoint = {
    x: WIDTH * (.5 + Math.sin((playerIndex - myIndex) * 2 *
        Math.PI / numPlayers) * distance / 2 * 0.9),
    y: HEIGHT * (.5 + Math.cos((playerIndex - myIndex) * 2 *
        Math.PI / numPlayers) * distance / 2),
  };

  let transformOrigin = "top center";
  if (180 < angle && angle <= 360 || -180 <= angle && angle < -0) {
    transformOrigin = "top left";
  }

  return (cardIds, selectedCardIds, cardsById, faceUp, selectCards) => {
    const interCardDistance = faceUp ? 15 : 9;
    const totalWidth = CARD_WIDTH + interCardDistance * (cardIds.length - 1);
    const cardPositions = {};
    cardIds.forEach((cardId, index) => {
      let x, y;

      if (centerPoint.y === HEIGHT/2) {
        x = 0;
        y = -totalWidth / 2 + interCardDistance * index;
      } else {
        const numerator = -(centerPoint.x-(WIDTH/2));
        const denominator = ((HEIGHT-centerPoint.y)-(HEIGHT/2));
        const slope = numerator/denominator * 0.7;
        const xStep = Math.sqrt(interCardDistance**2/(1+slope**2));
        const yStep = slope * xStep;
        const shift = selectedCardIds && selectedCardIds[cardId] ? -20 : 0;
        x = (-cardIds.length/2 + index) * xStep;
        y = (-cardIds.length/2 + index) * yStep - shift;
      }

      const src = faceUp ? getCardImageSrc(cardsById[cardId]) : getFaceDownCardImageSrc();
      const onClick = selectCards ? () => selectCards(cardId) : undefined;

      cardPositions[cardId] = {
        centerTop: centerPoint.y,
        centerLeft: centerPoint.x,
        top: centerPoint.y - y,
        left: centerPoint.x + x,
        src: src,
        angle: angle,
        zIndex: index,
        onClick: onClick,
        transformOrigin: transformOrigin
      }
    });
    return cardPositions;
  }
}
