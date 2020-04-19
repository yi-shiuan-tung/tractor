import * as React from 'react';
import * as ReactDOM from 'react-dom';
import { HashRouter, Route, Switch, useHistory } from 'react-router-dom';
import {Lobby} from './views/lobby';
import {Room} from './views/room';
import './index.css';

function App() {
  const history = useHistory();

  return <Switch>
    <Route
      exact
      path='/'
      render={() => <Lobby joinRoom={roomCode => history.push(`/${roomCode}`)} />}
    />
    <Route
      path='/:roomCode'
      render={({ match: { params: { roomCode } } }) => <Room roomCode={roomCode} leaveRoom={() => history.push('/')} />}
    />
  </Switch>;
}

ReactDOM.render(<HashRouter><App /></HashRouter>, document.getElementById('app'));
