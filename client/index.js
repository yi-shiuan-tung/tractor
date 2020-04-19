import * as React from 'react';
import * as ReactDOM from 'react-dom';
import GithubCorner from 'react-github-corner';
import { HashRouter, Route, Switch, useHistory } from 'react-router-dom';
import {Lobby} from './views/lobby';
import {Room} from './views/room';
import './index.css';

function App() {
  const history = useHistory();

  return <div>
    <Switch>
      <Route
        exact
        path='/'
        render={() => <Lobby joinRoom={roomCode => history.push(`/${roomCode}`)} />}
      />
      <Route
        path='/:roomCode'
        render={({ match: { params: { roomCode } } }) => <Room roomCode={roomCode} leaveRoom={() => history.push('/')} />}
      />
    </Switch>
    <GithubCorner href="https://github.com/ytung/tractor" />
  </div>;
}

ReactDOM.render(<HashRouter><App /></HashRouter>, document.getElementById('app'));
