import * as React from 'react';
import { ORDINALS, SUITS, VALUES } from '../../cards';
import "./FindAFriendPanel.css";

export class FindAFriendPanel extends React.Component {

    constructor(props) {
        super(props);
        this.state = this.getState(this.props.numFriends);
    }

    componentDidUpdate(prevProps) {
        const { numFriends } = this.props;
        if (numFriends !== prevProps.numFriends) {
            this.setState(this.getState(numFriends));
        }
    }

    getState(numFriends) {
        const state = {};
        for (let i = 0; i < numFriends; i++) {
            state[i] = {
                ordinal: 1,
                value: 'ACE',
                suit: Object.keys(SUITS)[i],
            };
        }
        return state;
    }

    render() {
        const { numFriends, setFindAFriendDeclaration } = this.props;
        return (
            <div className="find_a_friend_panel">
                <h3>Declare your friends</h3>
                {Array.from({length: numFriends}, (_, i) => this.renderRow(this.state[i], i))}
                <button
                    className='make_friend_button'
                    onClick={() => setFindAFriendDeclaration(Array.from({length: numFriends}, (_, i) => {
                        const declaration = this.state[i];
                        return {
                            ...declaration,
                            suit: declaration.value.endsWith('JOKER') ? 'JOKER' : declaration.suit,
                        };
                    }))}
                >
                    {"Submit"}
                </button>
            </div>
        )
    }

    renderRow({ ordinal, value, suit }, index) {
        return (
            <div key={index} className="friend_row">
                <div>{`Friend ${index + 1}`}</div>
                <select
                    value={ordinal}
                    onChange={e => this.setState({ [index]: { ordinal: e.target.value, value, suit } })}
                >
                    {ORDINALS.map((ordinal, index) => <option key={index} value={`${index}`}>{ordinal}</option>)}
                </select>
                <select
                    value={value}
                    onChange={e => this.setState({ [index]: { ordinal, value: e.target.value, suit } })}
                >
                    {Object.entries(VALUES).map(([key, value]) => <option key={key} value={`${key}`}>{value}</option>)}
                    <option key='SMALL_JOKER' value={'SMALL_JOKER'}>{'Small joker'}</option>
                    <option key='BIG_JOKER' value={'BIG_JOKER'}>{'Big joker'}</option>
                </select>
                {this.maybeRenderSuit({ordinal, value, suit}, index)}
            </div>
        );
    }

    maybeRenderSuit({ ordinal, value, suit }, index) {
        if (value.endsWith('JOKER')) {
            return;
        }
        return (
            <span>
                {"of"}
                <select
                    value={suit}
                    onChange={e => this.setState({ [index]: { ordinal, value, suit: e.target.value } })}
                >
                    {Object.entries(SUITS).map(([key, value]) => <option key={key} value={`${key}`}>{value}</option>)}
                </select>
            </span>
        )
    }
}
