
import * as React from "react";
import { WIDTH, HEIGHT } from "./game";

/*
 * Renders the given children in front of the given player (under the correct orientation).
 * The distance is a number from 0 (in the middle) to 1 (very close to the player)
 */
export class PlayerArea extends React.Component {

    render() {
        const { playerIds, playerId, myId, distance, isText, children } = this.props;
        const playerIndex = playerIds.indexOf(playerId);
        const myIndex = playerIds.indexOf(myId);
        const centerPoint = {
            x: WIDTH * (.5 + Math.sin((playerIndex - myIndex) * 2 * Math.PI / playerIds.length) * distance / 2),
            y: HEIGHT * (.5 + Math.cos((playerIndex - myIndex) * 2 * Math.PI / playerIds.length) * distance / 2),
        };
        let angle = (myIndex - playerIndex) * 360. / playerIds.length;
        if (isText) {
            // ensure text is always facing the player by possibly rotating 180 degrees
            if (angle > 90) {
                angle -= 180;
            } else if (angle <= -90) {
                angle += 180;
            }
        }
        let transform = `rotate(${angle}deg)`;
        if (isText) {
            // text should be centered around the middle
            // TODO figure out how to compute the text dimensions instead of this hacky adjustment
            transform = "translate(-60px) " + transform;
        }
        return (
            <div
                key={playerId}
                className="player_container"
                style={
                    {
                        top: centerPoint.y,
                        left: centerPoint.x,
                        transform,
                    }
                }>
                {children}
            </div>
        );
    }
}
