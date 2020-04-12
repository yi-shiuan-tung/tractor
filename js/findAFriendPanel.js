import * as React from 'react';
import { ORDINALS, SUITS, VALUES } from './cards';

export class FindAFriendPanel extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            ordinal: 1,
            value: 'TWO',
            suit: 'CLUB',
        }
    }

    render() {
        // Janky rendering for now for testing. TODO make a real panel
        const { playerIds, findAFriend, declarerPlayerIndex, status, myId, setFindAFriendDeclaration } = this.props;
        const { ordinal, value, suit } = this.state;
        if (!findAFriend || status !== 'MAKE_KITTY' || playerIds[declarerPlayerIndex] !== myId) {
            return <div />;
        }
        return (
            <div>
                {"Find a friend declaration:"}
                <select
                    id="find_a_friend_panel_select_1"
                    value={ordinal}
                    onChange={e => this.setState({ ordinal: e.target.value })}
                >
                    {ORDINALS.map((ordinal, index) => <option value={`${index}`}>{ordinal}</option>)}
                </select>
                <select
                    id="find_a_friend_panel_select_2"
                    value={value}
                    onChange={e => this.setState({ value: e.target.value })}
                >
                    {Object.entries(VALUES).map(([key, value]) => <option value={`${key}`}>{value}</option>)}
                </select>
                {"of"}
                <select
                    id="find_a_friend_panel_select_3"
                    value={suit}
                    onChange={e => this.setState({ suit: e.target.value })}
                >
                    {Object.entries(SUITS).map(([key, value]) => <option value={`${key}`}>{value}</option>)}
                </select>
                <button
                    type='button'
                    onClick={() => setFindAFriendDeclaration([{
                        ordinal,
                        value,
                        suit,
                    }])}
                >
                    {"Submit"}
                </button>
            </div>
        )
    }
}
