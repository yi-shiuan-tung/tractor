import PropTypes from 'prop-types';
import React from 'react';

const Card = ({
  x,
  y,
  src,
  onClick,
}) => <img
  style={
    {
      top: `${y}px`,
      left: `${x}px`,
    }
  }
  src={src}
  onClick={onClick}
/>;

Card.propTypes = {
  x: PropTypes.number,
  y: PropTypes.number,
  src: PropTypes.string,
  onClick: PropTypes.func,
};

export default Card;
