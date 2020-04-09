
export const VALUES = {
    'ACE': '1',
    'TWO': '2',
    'THREE': '3',
    'FOUR': '4',
    'FIVE': '5',
    'SIX': '6',
    'SEVEN': '7',
    'EIGHT': '8',
    'NINE': '9',
    'TEN': '10',
    'JACK': 'j',
    'QUEEN': 'q',
    'KING': 'k',
};

const SUITS = {
    'CLUB': 'c',
    'DIAMOND': 'd',
    'HEART': 'h',
    'SPADE': 's',
};

function getImageSrc(name) {
    return `./images/${name}.gif`;
}

function getImageName(card) {
    if (card.value == 'SMALL_JOKER') {
        return 'jb';
    } else if (card.value == 'BIG_JOKER') {
        return 'jr';
    } else {
        return `${SUITS[card.suit]}${VALUES[card.value]}`
    }
}

export function getFaceDownCardImageSrc(card) {
    return getImageSrc('b1fv');
}

export function getCardImageSrc(card) {
    return getImageSrc(getImageName(card));
}
