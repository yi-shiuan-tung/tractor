import gameStart from '../assets/audio/game_start.mp3';
import yourTurn from '../assets/audio/your_turn.mp3';
import gameOver from '../assets/audio/game_over.mp3';

function play(audio, file) {
    if (audio.volume > 0) {
        audio.src = file;
        audio.currentTime = 0;
        audio.play();
    }
}

/**
 * Provides convenience functions to play all audio files.
 * The input volume is an integer from 0 to 3 (inclusive).
 */
export const getAudio = function () {
    const audio = new Audio();
    audio.volume = 1;

    return {
        setVolume: volume => audio.volume = volume / 3,
        playGameStart: () => play(audio, gameStart),
        playYourTurn: () => play(audio, yourTurn),
        playGameOver: () => play(audio, gameOver),
    };
}
