
const VALUES = Object.freeze({
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
});

const SUITS = Object.freeze({
  'CLUB': 'c',
  'DIAMOND': 'd',
  'HEART': 'h',
  'SPADE': 's',
});

function getImageSrc(name) {
  return `./images/${name}.gif`;
}

function getImageName(card) {
  if (card.value == 'SMALL_JOKER') {
    return 'jb';
  } else if (card.value == 'BIG_JOKER') {
    return 'jr';
  } else {
    return `${SUITS[card.suit]}${VALUES[card.value]}`;
  }
}

export function preloadCardImages() {
  for (const value in VALUES) {
    for (const suit in SUITS) {
      const img = new Image();
      img.src = getImageSrc(getImageName({ value: value, suit: suit }));
    }
  }
  {
    const img = new Image();
    img.src = getImageSrc(getImageName({ value: 'BIG_JOKER' }));
  }
  {
    const img = new Image();
    img.src = getImageSrc(getImageName({ value: 'SMALL_JOKER' }));
  }
}

export function getFaceDownCardImageSrc() {
  return getImageSrc('b1fv');
}

export function getCardImageSrc(card) {
  if (card === undefined) {
    return getFaceDownCardImageSrc();
  }
  return getImageSrc(getImageName(card));
}
