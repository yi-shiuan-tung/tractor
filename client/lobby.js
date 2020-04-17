import PropTypes from 'prop-types';
import * as React from 'react';
import {setUpConnection} from './providers/connection';
import {LOCATION} from './lib/consts';

export class Lobby extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      inputRoomCode: '',
    };
  }

  componentDidMount() {
    const {joinRoom} = this.props;
    this.subSocket = setUpConnection(
        LOCATION + 'tractor',
        undefined,
        (response) => {
          const message = response.responseBody;
          console.log('Received message: ' + message);

          const json = JSON.parse(message);

          if (json.CREATE_ROOM || json.JOIN_ROOM) {
            const roomCode = json.CREATE_ROOM ?
            json.CREATE_ROOM.roomCode : json.JOIN_ROOM.roomCode;
            joinRoom(roomCode);
          } else {
            console.log('Unhandled message: ' + JSON.stringify(json));
          }
        });
  }

  componentWillUnmount() {
    this.subSocket.disconnect();
  }

    connectToRoom = () => {
      const {inputRoomCode} = this.state;
      this.subSocket.push(
          JSON.stringify({'JOIN_ROOM': {'roomCode': inputRoomCode}}),
      );
    }

    render() {
      const {inputRoomCode} = this.state;
      return (
        <div className='lobbyContainer'>
          <h1 className='title' style={{'fontFamily': 'Play'}}>🚜Tractor</h1>
          <div id='joinContainer'>
            <input
              type='text'
              value={inputRoomCode}
              onChange={(e) => this.setState({inputRoomCode: e.target.value.toUpperCase()})}
              onKeyPress = {
                (e) => {
                  if (e.nativeEvent.key == 'Enter') {
                    this.connectToRoom();
                    e.stopPropagation();
                  }
                }
              }
            />
            <div
              className='button primary'
              onClick={this.connectToRoom}
            >
            Join existing game
            </div>
          </div>
          <div
            className='button primary'
            onClick={() => {
              this.subSocket.push(JSON.stringify({'CREATE_ROOM': {}}));
            }}
          >
            Create new game
          </div>
        </div>
      );
    }
}

Lobby.propTypes = {
  joinRoom: PropTypes.func,
};
