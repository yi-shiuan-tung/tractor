import * as React from 'react';

export class CenteredText extends React.Component {

  constructor(props) {
    super(props);
    this.state = {
      textWidth: undefined,
    };
  }

  render() {
    const { x, y, angle, children, ...otherProps } = this.props;
    const { textWidth } = this.state;
    let transform = `rotateZ(${angle}deg)`;
    let ref = undefined;
    // In the first loop, render the text anywhere and compute its width from
    // the ref. In subsequent renders, the transform will be correct, so we
    // can remove the ref (and avoid an infinite loop).
    if (textWidth) {
      transform = `translate(-${textWidth / 2}px) ${transform}`;
    } else {
      ref = (el) => {
        if (el) {
          this.setState({ textWidth: el.clientWidth });
        }
      };
    }
    return (
      <div
        style={{
          position: 'absolute',
          left: x,
          top: y,
          transform,
        }}
        ref={ref}
        {...otherProps}
      >
        {children}
      </div>
    )
  }
}
