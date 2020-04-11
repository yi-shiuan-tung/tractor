import * as React from 'react';

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
        const { findAFriend, status, setFindAFriendDeclaration } = this.props;
        const { ordinal, value, suit } = this.state;
        if (!findAFriend || status !== 'MAKE_KITTY') {
            return <div />;
        }
        return (
            <div>
                Find a friend declaration:
                <select
                    id="find_a_friend_panel_select_1"
                    value={ordinal}
                    onChange={e => this.setState({ ordinal: e.target.value })}
                >
                    <option value="0">OTHER</option>
                    <option value="1">FIRST</option>
                    <option value="2">SECOND</option>
                </select>
                <select
                    id="find_a_friend_panel_select_2"
                    value={value}
                    onChange={e => this.setState({ value: e.target.value })}
                >
                    <option value="TWO">2</option>
                    <option value="THREE">3</option>
                    <option value="FOUR">4</option>
                </select>
                of
                <select
                    id="find_a_friend_panel_select_3"
                    value={suit}
                    onChange={e => this.setState({ suit: e.target.value })}
                >
                    <option value="CLUB">clubs</option>
                    <option value="DIAMOND">diamonds</option>
                    <option value="HEART">hearts</option>
                    <option value="SPADE">spades</option>
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
